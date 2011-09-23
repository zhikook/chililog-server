// Copyright 2010 Cinch Logic Pty Ltd.

package org.chililog.server.pubsub;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.regex.Pattern;

import org.chililog.client.websocket.WebSocketCallback;
import org.chililog.client.websocket.WebSocketClient;
import org.chililog.client.websocket.WebSocketClientFactory;
import org.chililog.server.common.JsonTranslator;
import org.chililog.server.common.Log4JLogger;
import org.chililog.server.data.MongoConnection;
import org.chililog.server.data.RepositoryConfigBO;
import org.chililog.server.data.RepositoryConfigController;
import org.chililog.server.data.UserBO;
import org.chililog.server.data.UserController;
import org.chililog.server.engine.MqService;
import org.chililog.server.engine.RepositoryService;
import org.chililog.server.pubsub.jsonhttp.JsonHttpService;
import org.chililog.server.pubsub.jsonhttp.LogEntryAO;
import org.chililog.server.pubsub.jsonhttp.PublicationRequestAO;
import org.chililog.server.pubsub.jsonhttp.PublicationResponseAO;
import org.chililog.server.pubsub.jsonhttp.SubscriptionRequestAO;
import org.chililog.server.pubsub.jsonhttp.SubscriptionResponseAO;
import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame;
import org.jboss.netty.handler.codec.http.websocket.WebSocketFrame;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

/**
 * Test web socket standard hybi 00
 * 
 * @author vibul
 * 
 */
public class JsonWebSocketTest
{
    private static DB _db;
    private static RepositoryConfigBO _repoInfo;

    private static final String REPOSITORY_NAME = "json_ws_test";
    private static final String MONGODB_COLLECTION_NAME = "repo_json_ws_test";

    @BeforeClass
    public static void classSetup() throws Exception
    {
        // Create repo
        _repoInfo = new RepositoryConfigBO();
        _repoInfo.setName(REPOSITORY_NAME);
        _repoInfo.setDisplayName("Json Web Socket Test");
        _repoInfo.setStoreEntriesIndicator(true);
        _repoInfo.setStorageQueueDurableIndicator(false);
        _repoInfo.setStorageQueueWorkerCount(2);

        // Database
        _db = MongoConnection.getInstance().getConnection();
        assertNotNull(_db);

        // Clean up old users
        DBCollection coll = _db.getCollection(UserController.MONGODB_COLLECTION_NAME);
        Pattern pattern = Pattern.compile("^JsonWsTestUser[\\w]*$");
        DBObject query = new BasicDBObject();
        query.put("username", pattern);
        coll.remove(query);

        // Clean old repository info
        coll = _db.getCollection(RepositoryConfigController.MONGODB_COLLECTION_NAME);
        pattern = Pattern.compile("^" + REPOSITORY_NAME + "$");
        query = new BasicDBObject();
        query.put("name", pattern);
        coll.remove(query);

        // Clean up old test data if any exists
        coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        if (coll != null)
        {
            coll.drop();
        }

        // Create repository record
        RepositoryConfigController.getInstance().save(_db, _repoInfo);

        // Create user that cannot access this repository
        UserBO user = new UserBO();
        user.setUsername("JsonWsTestUser_NoAccess");
        user.setPassword("111", true);
        UserController.getInstance().save(_db, user);

        // Create publisher user
        user = new UserBO();
        user.setUsername("JsonWsTestUser_Publisher");
        user.setPassword("222", true);
        user.addRole(_repoInfo.getPublisherRoleName());
        UserController.getInstance().save(_db, user);

        // Create subscriber user
        user = new UserBO();
        user.setUsername("JsonWsTestUser_Subscriber");
        user.setPassword("333", true);
        user.addRole(_repoInfo.getSubscriberRoleName());
        UserController.getInstance().save(_db, user);

        // Start it up
        MqService.getInstance().start();
        RepositoryService.getInstance().start();
        JsonHttpService.getInstance().start();
    }

    @Before
    public void testSetup() throws Exception
    {
        // Drop collection for each test
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        if (coll != null)
        {
            coll.drop();
        }
    }

    @AfterClass
    public static void classTeardown() throws Exception
    {
        // Stop it all
        JsonHttpService.getInstance().stop();
        RepositoryService.getInstance().stop();
        MqService.getInstance().stop();

        // Drop collection
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        if (coll != null)
        {
            coll.drop();
        }
        
        // Clean up users
        coll = _db.getCollection(UserController.MONGODB_COLLECTION_NAME);
        Pattern pattern = Pattern.compile("^JsonWsTestUser[\\w]*$");
        DBObject query = new BasicDBObject();
        query.put("username", pattern);
        coll.remove(query);
        
        // Clean old repository info
        coll = _db.getCollection(RepositoryConfigController.MONGODB_COLLECTION_NAME);
        pattern = Pattern.compile("^" + REPOSITORY_NAME + "$");
        query = new BasicDBObject();
        query.put("name", pattern);
        coll.remove(query);        
    }

    /**
     * Sends a publish request over ws
     * 
     * @param client
     *            Web socket client
     * @param callbackHandler
     *            Callback handler to handle incoming responses
     * @param msgID
     *            unique message id
     * @param entryCount
     *            number of log entries to send
     * @throws Exception
     */
    public static void sendPublicshRequest(WebSocketClient client,
                                           PublishCallbackHandler callbackHandler,
                                           String msgID,
                                           int entryCount) throws Exception
    {
        PublicationRequestAO request = new PublicationRequestAO();
        request.setMessageID(msgID);
        request.setUsername("JsonWsTestUser_Publisher");
        request.setPassword("222");
        request.setRepositoryName(REPOSITORY_NAME);

        LogEntryAO[] logEntries = new LogEntryAO[entryCount];

        for (int i = 0; i < entryCount; i++)
        {
            LogEntryAO logEntry = new LogEntryAO();
            logEntry.setTimestamp("2011-01-01T00:00:00.000Z");
            logEntry.setSource("junit");
            logEntry.setHost("localhost");
            logEntry.setSeverity("4");
            logEntry.setMessage("test message " + i);
            logEntries[i] = logEntry;
        }
        request.setLogEntries(logEntries);

        // Send request
        callbackHandler.messageReceived = null;
        String requestJson = JsonTranslator.getInstance().toJson(request);
        client.send(new DefaultWebSocketFrame(requestJson));

        // Wait for it to be processed
        Thread.sleep(1000);

        // Check response
        assertNotNull(callbackHandler.messageReceived);
        PublicationResponseAO response = JsonTranslator.getInstance().fromJson(callbackHandler.messageReceived,
                PublicationResponseAO.class);
        assertEquals(msgID, response.getMessageID());
        assertTrue(response.isSuccess());
        assertNull(response.getErrorMessage());
        assertNull(response.getErrorStackTrace());
    }

   
    @Test
    public void testPublishOneLogEntry() throws Exception
    {
        PublishCallbackHandler callbackHandler = new PublishCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Publish
        sendPublicshRequest(client, callbackHandler, "testPublishOneLogEntry", 1);

        // Disconnect
        // Test that all is OK when shutting down server with open connections

        // Check database
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        assertEquals(1, coll.find().count());

    }

    @Test
    public void testPublishManyLogEntries() throws Exception
    {
        PublishCallbackHandler callbackHandler = new PublishCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Publish
        sendPublicshRequest(client, callbackHandler, "testPublishManyLogEntries", 10);

        // Disconnect - check that all is OK if shutdown server with no open connections
        client.disconnect();

        // Check database
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        assertEquals(10, coll.find().count());

    }

    @Test
    public void testPublishSubsequentLogEntries() throws Exception
    {
        PublishCallbackHandler callbackHandler = new PublishCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Publish
        sendPublicshRequest(client, callbackHandler, "testPublishSubsequentLogEntries", 1);
        sendPublicshRequest(client, callbackHandler, "testPublishSubsequentLogEntries", 2);
        sendPublicshRequest(client, callbackHandler, "testPublishSubsequentLogEntries", 3);

        // Disconnect - check that all is OK if shutdown server with no open connections
        client.disconnect();

        // Check database
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        assertEquals(6, coll.find().count());
    }

    @Test
    public void testPublishMultipleConnections() throws Exception
    {
        // 20 threads each adding 2 log entries = 40 log entries in total
        for (int i = 0; i < 20; i++)
        {
            PublishThread runnable = new PublishThread();
            Thread thread = new Thread(runnable);
            thread.start();
        }

        // Wait a moment for log entry to be processed
        Thread.sleep(5000);

        // Check that the entry is written to the log
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        assertEquals(40, coll.find().count());

    }

    @Test
    public void testSubscribeMultipleConnections() throws Exception
    {
        // Subscribe
        SubscribeCallbackHandler callbackHandler = new SubscribeCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient subcriberClient = factory.newClient(new URI("ws://localhost:61615/websocket"),
                callbackHandler);
        subcriberClient.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        SubscriptionRequestAO request = new SubscriptionRequestAO();
        request.setMessageID("testSubscribeMultipleConnections");
        request.setUsername("JsonWsTestUser_Subscriber");
        request.setPassword("333");
        request.setRepositoryName(REPOSITORY_NAME);

        String requestJson = JsonTranslator.getInstance().toJson(request);
        subcriberClient.send(new DefaultWebSocketFrame(requestJson));
        Thread.sleep(500);

        assertEquals(1, callbackHandler.messagesReceived.size());

        SubscriptionResponseAO response = JsonTranslator.getInstance().fromJson(
                callbackHandler.messagesReceived.get(0), SubscriptionResponseAO.class);
        assertEquals("testSubscribeMultipleConnections", response.getMessageID());
        assertTrue(response.isSuccess());
        assertNull(response.getErrorMessage());
        assertNull(response.getErrorStackTrace());

        callbackHandler.messagesReceived.clear();

        // Publish 20 threads each adding 2 log entries = 40 log entries in total
        for (int i = 0; i < 20; i++)
        {
            PublishThread runnable = new PublishThread();
            Thread thread = new Thread(runnable);
            thread.start();
        }

        // Wait a moment for log entry to be processed
        Thread.sleep(3000);

        assertEquals(40, callbackHandler.messagesReceived.size());
        response = JsonTranslator.getInstance().fromJson(callbackHandler.messagesReceived.get(0),
                SubscriptionResponseAO.class);
        assertEquals("testSubscribeMultipleConnections", response.getMessageID());
        assertTrue(response.isSuccess());
        assertNull(response.getErrorMessage());
        assertNull(response.getErrorStackTrace());

        LogEntryAO logEntry = response.getLogEntry();
        assertEquals("2011-01-01T00:00:00.000Z", logEntry.getTimestamp());
        assertEquals("localhost", logEntry.getHost());
        assertEquals("junit", logEntry.getSource());
        assertEquals("4", logEntry.getSeverity());
        assertEquals("test message 0", logEntry.getMessage());

    }

    @Test
    public void testUnsupportRequest() throws Exception
    {
        PublishCallbackHandler callbackHandler = new PublishCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);
    
        // Send request
        callbackHandler.messageReceived = null;
        String requestJson = JsonTranslator.getInstance().toJson("this is a string so that it cannot be recognised as a message");
                
        client.send(new DefaultWebSocketFrame(requestJson));

        // Wait for it to be processed
        Thread.sleep(1000);

        // Check response
        // Server should just shutdown socket because it does not recognise the request
        assertNull(callbackHandler.messageReceived);
        assertFalse(callbackHandler.connected);

        // Check database
        // Error in parsing means not stored entry
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        assertEquals(0, coll.find().count());
    }
    
    @Test
    public void testPublishBadJSON() throws Exception
    {
        PublishCallbackHandler callbackHandler = new PublishCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Publish
        PublicationRequestAO request = new PublicationRequestAO();
        request.setMessageID("testPublishBadLogEntry");
        request.setUsername("JsonWsTestUser_Publisher");
        request.setPassword("222");
        request.setRepositoryName(REPOSITORY_NAME);

        LogEntryAO[] logEntries = new LogEntryAO[1];

        for (int i = 0; i < 1; i++)
        {
            LogEntryAO logEntry = new LogEntryAO();
            logEntry.setTimestamp("2011-01-01T00:00:00.000Z");
            logEntry.setSource("junit");
            logEntry.setHost("localhost");
            logEntry.setSeverity("4");
            logEntry.setMessage("test message " + i);
            logEntries[i] = logEntry;
        }
        request.setLogEntries(logEntries);

        // Send request
        callbackHandler.messageReceived = null;
        String requestJson = JsonTranslator.getInstance().toJson(request);
        requestJson = requestJson.replace("MessageID", "MessageID: \"big stuff up to json syntax\" ");
                
        client.send(new DefaultWebSocketFrame(requestJson));

        // Wait for it to be processed
        Thread.sleep(1000);

        // Check response
        assertNotNull(callbackHandler.messageReceived);
        PublicationResponseAO response = JsonTranslator.getInstance().fromJson(callbackHandler.messageReceived,
                PublicationResponseAO.class);
        
        // Still OK because the error is in the parsing done by subscribers
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getErrorStackTrace());

        // Disconnect
        client.disconnect();

        // Check database
        // Error in parsing means not stored entry
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        assertEquals(0, coll.find().count());
    }
    
    @Test
    public void testPublishBadLogEntry() throws Exception
    {
        PublishCallbackHandler callbackHandler = new PublishCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Publish
        PublicationRequestAO request = new PublicationRequestAO();
        request.setMessageID("testPublishBadLogEntry");
        request.setUsername("JsonWsTestUser_Publisher");
        request.setPassword("222");
        request.setRepositoryName(REPOSITORY_NAME);

        LogEntryAO[] logEntries = new LogEntryAO[1];

        for (int i = 0; i < 1; i++)
        {
            LogEntryAO logEntry = new LogEntryAO();
            logEntry.setTimestamp("xxxx");          // Unparsable Date 
            logEntry.setSource("junit");
            logEntry.setHost("localhost");
            logEntry.setSeverity("4");
            logEntry.setMessage("test message " + i);
            logEntries[i] = logEntry;
        }
        request.setLogEntries(logEntries);

        // Send request
        callbackHandler.messageReceived = null;
        String requestJson = JsonTranslator.getInstance().toJson(request);
                
        client.send(new DefaultWebSocketFrame(requestJson));

        // Wait for it to be processed
        Thread.sleep(1000);

        // Check response
        assertNotNull(callbackHandler.messageReceived);
        PublicationResponseAO response = JsonTranslator.getInstance().fromJson(callbackHandler.messageReceived,
                PublicationResponseAO.class);
        assertEquals("testPublishBadLogEntry", response.getMessageID());
        
        // Still OK because the error is in the parsing done by subscribers
        assertTrue(response.isSuccess());
        assertNull(response.getErrorMessage());
        assertNull(response.getErrorStackTrace());

        // Disconnect
        // Test that all is OK when shutting down server with open connections

        // Check database
        // Error in parsing means not stored entry
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        assertEquals(0, coll.find().count());
    }

    @Test
    public void testPublishBadUsername() throws Exception
    {
        PublishCallbackHandler callbackHandler = new PublishCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Prepare request
        PublicationRequestAO request = new PublicationRequestAO();
        request.setMessageID("testBadUsername");
        request.setUsername("XXX");
        request.setPassword("222");
        request.setRepositoryName(REPOSITORY_NAME);

        LogEntryAO[] logEntries = new LogEntryAO[1];
        for (int i = 0; i < 1; i++)
        {
            LogEntryAO logEntry = new LogEntryAO();
            logEntry.setTimestamp("2011-01-01T00:00:00.000Z");
            logEntry.setSource("junit");
            logEntry.setHost("localhost");
            logEntry.setSeverity("4");
            logEntry.setMessage("test message " + i);
            logEntries[i] = logEntry;
        }
        request.setLogEntries(logEntries);

        // Send request
        callbackHandler.messageReceived = null;
        String requestJson = JsonTranslator.getInstance().toJson(request);
        client.send(new DefaultWebSocketFrame(requestJson));

        // Wait for it to be processed
        Thread.sleep(1000);

        // Disconnect
        client.disconnect();

        // Check response
        assertNotNull(callbackHandler.messageReceived);
        PublicationResponseAO response = JsonTranslator.getInstance().fromJson(callbackHandler.messageReceived,
                PublicationResponseAO.class);
        assertEquals("testBadUsername", response.getMessageID());
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getErrorStackTrace());
        assertEquals("Cannot find user 'XXX'.", response.getErrorMessage());
    }

    @Test
    public void testPublishBadPassword() throws Exception
    {
        PublishCallbackHandler callbackHandler = new PublishCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Prepare request
        PublicationRequestAO request = new PublicationRequestAO();
        request.setMessageID("testBadPassword");
        request.setUsername("JsonWsTestUser_Publisher");
        request.setPassword("bad");
        request.setRepositoryName(REPOSITORY_NAME);

        LogEntryAO[] logEntries = new LogEntryAO[1];
        for (int i = 0; i < 1; i++)
        {
            LogEntryAO logEntry = new LogEntryAO();
            logEntry.setTimestamp("2011-01-01T00:00:00.000Z");
            logEntry.setSource("junit");
            logEntry.setHost("localhost");
            logEntry.setSeverity("4");
            logEntry.setMessage("test message " + i);
            logEntries[i] = logEntry;
        }
        request.setLogEntries(logEntries);

        // Send request
        callbackHandler.messageReceived = null;
        String requestJson = JsonTranslator.getInstance().toJson(request);
        client.send(new DefaultWebSocketFrame(requestJson));

        // Wait for it to be processed
        Thread.sleep(1000);

        // Disconnect
        client.disconnect();

        // Check response
        assertNotNull(callbackHandler.messageReceived);
        PublicationResponseAO response = JsonTranslator.getInstance().fromJson(callbackHandler.messageReceived,
                PublicationResponseAO.class);
        assertEquals("testBadPassword", response.getMessageID());
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getErrorStackTrace());
        assertEquals("Access denied.", response.getErrorMessage());
    }

    @Test
    public void testPublishBadRole() throws Exception
    {
        PublishCallbackHandler callbackHandler = new PublishCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Prepare request
        PublicationRequestAO request = new PublicationRequestAO();
        request.setMessageID("testBadRole");
        request.setUsername("JsonWsTestUser_NoAccess");
        request.setPassword("111");
        request.setRepositoryName(REPOSITORY_NAME);

        LogEntryAO[] logEntries = new LogEntryAO[1];
        for (int i = 0; i < 1; i++)
        {
            LogEntryAO logEntry = new LogEntryAO();
            logEntry.setTimestamp("2011-01-01T00:00:00.000Z");
            logEntry.setSource("junit");
            logEntry.setHost("localhost");
            logEntry.setSeverity("4");
            logEntry.setMessage("test message " + i);
            logEntries[i] = logEntry;
        }
        request.setLogEntries(logEntries);

        // Send request
        callbackHandler.messageReceived = null;
        String requestJson = JsonTranslator.getInstance().toJson(request);
        client.send(new DefaultWebSocketFrame(requestJson));

        // Wait for it to be processed
        Thread.sleep(1000);

        // Disconnect
        client.disconnect();

        // Check response
        assertNotNull(callbackHandler.messageReceived);
        PublicationResponseAO response = JsonTranslator.getInstance().fromJson(callbackHandler.messageReceived,
                PublicationResponseAO.class);
        assertEquals("testBadRole", response.getMessageID());
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getErrorStackTrace());
        assertEquals("Access denied.", response.getErrorMessage());
    }

    @Test
    public void testSubscribeBadUsername() throws Exception
    {
        SubscribeCallbackHandler callbackHandler = new SubscribeCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Prepare request
        SubscriptionRequestAO request = new SubscriptionRequestAO();
        request.setMessageID("testBadUsername");
        request.setUsername("XXX");
        request.setPassword("222");
        request.setRepositoryName(REPOSITORY_NAME);

        // Send request
        String requestJson = JsonTranslator.getInstance().toJson(request);
        client.send(new DefaultWebSocketFrame(requestJson));

        // Wait for it to be processed
        Thread.sleep(1000);

        // Disconnect
        client.disconnect();

        // Check response
        assertEquals(1, callbackHandler.messagesReceived.size());
        SubscriptionResponseAO response = JsonTranslator.getInstance().fromJson(
                callbackHandler.messagesReceived.get(0), SubscriptionResponseAO.class);
        assertEquals("testBadUsername", response.getMessageID());
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getErrorStackTrace());
        assertEquals("Cannot find user 'XXX'.", response.getErrorMessage());
    }

    @Test
    public void testSubscribeBadPassword() throws Exception
    {
        SubscribeCallbackHandler callbackHandler = new SubscribeCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Prepare request
        SubscriptionRequestAO request = new SubscriptionRequestAO();
        request.setMessageID("testBadPassword");
        request.setUsername("JsonWsTestUser_Publisher");
        request.setPassword("bad");
        request.setRepositoryName(REPOSITORY_NAME);

        // Send request
        String requestJson = JsonTranslator.getInstance().toJson(request);
        client.send(new DefaultWebSocketFrame(requestJson));

        // Wait for it to be processed
        Thread.sleep(1000);

        // Disconnect
        client.disconnect();

        // Check response
        assertEquals(1, callbackHandler.messagesReceived.size());
        SubscriptionResponseAO response = JsonTranslator.getInstance().fromJson(
                callbackHandler.messagesReceived.get(0), SubscriptionResponseAO.class);
        assertEquals("testBadPassword", response.getMessageID());
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getErrorStackTrace());
        assertEquals("PubSub.SubscriberAuthenticationError", response.getErrorMessage());
    }

    @Test
    public void testSubscribeBadRole() throws Exception
    {
        SubscribeCallbackHandler callbackHandler = new SubscribeCallbackHandler();
        WebSocketClientFactory factory = new WebSocketClientFactory();

        WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"), callbackHandler);

        // Connect
        client.connect().awaitUninterruptibly();
        Thread.sleep(500);
        assertTrue(callbackHandler.connected);

        // Prepare request
        SubscriptionRequestAO request = new SubscriptionRequestAO();
        request.setMessageID("testBadRole");
        request.setUsername("JsonWsTestUser_NoAccess");
        request.setPassword("111");
        request.setRepositoryName(REPOSITORY_NAME);

        // Send request
        String requestJson = JsonTranslator.getInstance().toJson(request);
        client.send(new DefaultWebSocketFrame(requestJson));

        // Wait for it to be processed
        Thread.sleep(1000);

        // Disconnect
        client.disconnect();

        // Check response
        assertEquals(1, callbackHandler.messagesReceived.size());
        SubscriptionResponseAO response = JsonTranslator.getInstance().fromJson(
                callbackHandler.messagesReceived.get(0), SubscriptionResponseAO.class);
        assertEquals("testBadRole", response.getMessageID());
        assertFalse(response.isSuccess());
        assertNotNull(response.getErrorMessage());
        assertNotNull(response.getErrorStackTrace());
        assertEquals("Access denied.", response.getErrorMessage());
    }

    /**
     * Thread for running in testMultipleConnections
     * 
     * @author vibul
     * 
     */
    public static class PublishThread implements Runnable
    {
        private static Log4JLogger _logger = Log4JLogger.getLogger(PublishThread.class);

        public void run()
        {
            try
            {
                _logger.debug("WS thread " + Thread.currentThread().getName() + " started");

                PublishCallbackHandler callbackHandler = new PublishCallbackHandler();
                WebSocketClientFactory factory = new WebSocketClientFactory();

                WebSocketClient client = factory.newClient(new URI("ws://localhost:61615/websocket"),
                        callbackHandler);

                // Connect
                client.connect().awaitUninterruptibly();
                Thread.sleep(500);
                assertTrue(callbackHandler.connected);

                // Publish
                sendPublicshRequest(client, callbackHandler, "PublishThread" + Thread.currentThread().getName(), 2);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Our web socket callback handler for publishing
     * 
     * @author vibul
     * 
     */
    public static class PublishCallbackHandler implements WebSocketCallback
    {
        private static Log4JLogger _logger = Log4JLogger.getLogger(PublishCallbackHandler.class);

        public boolean connected = false;
        public String messageReceived = null;

        public PublishCallbackHandler()
        {
            return;
        }

        @Override
        public void onConnect(WebSocketClient client)
        {
            _logger.debug("Publish WebSocket connected!");
            connected = true;
        }

        @Override
        public void onDisconnect(WebSocketClient client)
        {
            _logger.debug("Publish WebSocket disconnected!");
            connected = false;
        }

        @Override
        public void onMessage(WebSocketClient client, WebSocketFrame frame)
        {
            _logger.debug("Publish WebSocket Received Message:" + frame.getTextData());
            messageReceived = frame.getTextData();
        }

        @Override
        public void onError(Throwable t)
        {
            _logger.error(t, "Publish WebSocket error");
        }

    }

    /**
     * Our web socket callback handler for publishing
     * 
     * @author vibul
     * 
     */
    public static class SubscribeCallbackHandler implements WebSocketCallback
    {
        private static Log4JLogger _logger = Log4JLogger.getLogger(SubscribeCallbackHandler.class);

        public boolean connected = false;
        public ArrayList<String> messagesReceived = new ArrayList<String>();

        public SubscribeCallbackHandler()
        {
            return;
        }

        @Override
        public void onConnect(WebSocketClient client)
        {
            _logger.debug("Subscribe WebSocket connected!");
            connected = true;
        }

        @Override
        public void onDisconnect(WebSocketClient client)
        {
            _logger.debug("Subscribe WebSocket disconnected!");
            connected = false;
        }

        @Override
        public void onMessage(WebSocketClient client, WebSocketFrame frame)
        {
            _logger.debug("Subscribe WebSocket Received Message:" + frame.getTextData());
            messagesReceived.add(frame.getTextData());
        }

        @Override
        public void onError(Throwable t)
        {
            _logger.error(t, "Subscribe WebSocket error");
        }

    }
}
