//
// Copyright 2010 Cinch Logic Pty Ltd.
//
// http://www.chililog.com
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.chililog.server.engine;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.hornetq.api.core.Message;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.management.QueueControl;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import com.chililog.server.common.AppProperties;
import com.chililog.server.common.ChiliLogException;
import com.chililog.server.data.MongoConnection;
import com.chililog.server.data.RepositoryFieldInfoBO;
import com.chililog.server.data.RepositoryInfoBO;
import com.chililog.server.data.RepositoryParserInfoBO;
import com.chililog.server.data.RepositoryInfoBO.Status;
import com.chililog.server.data.RepositoryParserInfoBO.ParseFieldErrorHandling;
import com.chililog.server.data.RepositoryParserInfoBO.AppliesTo;
import com.chililog.server.engine.parsers.DelimitedEntryParser;
import com.mongodb.DB;
import com.mongodb.DBCollection;

/**
 * Test a repository
 * 
 * @author vibul
 * 
 */
public class RepositoryTest
{
    private static DB _db;
    private static RepositoryInfoBO _repoInfo;

    private static final String REPOSITORY_NAME = "junit_test";
    private static final String REPOSITORY_PUBLISHER_PASSWORD = "pw4publisher";
    private static final String REPOSITORY_SUBSCRIBER_PASSWORD = "pw4subscriber";
    private static final String MONGODB_COLLECTION_NAME = "repo_junit_test";

    @BeforeClass
    public static void classSetup() throws Exception
    {
        // Create repo
        _repoInfo = new RepositoryInfoBO();
        _repoInfo.setName(REPOSITORY_NAME);
        _repoInfo.setDisplayName("Repository Test 1");
        _repoInfo.setPublisherPassword(REPOSITORY_PUBLISHER_PASSWORD);
        _repoInfo.setSubscriberPassword(REPOSITORY_SUBSCRIBER_PASSWORD);
        _repoInfo.setStoreEntriesIndicator(true);
        _repoInfo.setStorageQueueDurableIndicator(false);
        _repoInfo.setStorageQueueWorkerCount(2);

        RepositoryParserInfoBO repoParserInfo = new RepositoryParserInfoBO();
        repoParserInfo.setName("parser1");
        repoParserInfo.setAppliesTo(AppliesTo.All);
        repoParserInfo.setClassName(DelimitedEntryParser.class.getName());
        repoParserInfo.setParseFieldErrorHandling(ParseFieldErrorHandling.SkipEntry);
        repoParserInfo.getProperties().put(DelimitedEntryParser.DELIMITER_PROPERTY_NAME, "|");
        _repoInfo.getParsers().add(repoParserInfo);

        RepositoryFieldInfoBO repoFieldInfo = new RepositoryFieldInfoBO();
        repoFieldInfo.setName("field1");
        repoFieldInfo.setDataType(RepositoryFieldInfoBO.DataType.String);
        repoFieldInfo.getProperties().put(DelimitedEntryParser.POSITION_FIELD_PROPERTY_NAME, "1");
        repoParserInfo.getFields().add(repoFieldInfo);

        repoFieldInfo = new RepositoryFieldInfoBO();
        repoFieldInfo.setName("field2");
        repoFieldInfo.setDataType(RepositoryFieldInfoBO.DataType.Integer);
        repoFieldInfo.getProperties().put(DelimitedEntryParser.POSITION_FIELD_PROPERTY_NAME, "2");
        repoParserInfo.getFields().add(repoFieldInfo);

        repoFieldInfo = new RepositoryFieldInfoBO();
        repoFieldInfo.setName("field3");
        repoFieldInfo.setDataType(RepositoryFieldInfoBO.DataType.Long);
        repoFieldInfo.getProperties().put(DelimitedEntryParser.POSITION_FIELD_PROPERTY_NAME, "3");
        repoParserInfo.getFields().add(repoFieldInfo);

        repoFieldInfo = new RepositoryFieldInfoBO();
        repoFieldInfo.setName("field4");
        repoFieldInfo.setDataType(RepositoryFieldInfoBO.DataType.Double);
        repoFieldInfo.getProperties().put(DelimitedEntryParser.POSITION_FIELD_PROPERTY_NAME, "4");
        repoParserInfo.getFields().add(repoFieldInfo);

        repoFieldInfo = new RepositoryFieldInfoBO();
        repoFieldInfo.setName("field5");
        repoFieldInfo.setDataType(RepositoryFieldInfoBO.DataType.Date);
        repoFieldInfo.getProperties().put(DelimitedEntryParser.POSITION_FIELD_PROPERTY_NAME, "5");
        repoFieldInfo.getProperties().put(RepositoryFieldInfoBO.DATE_FORMAT_PROPERTY_NAME, "yyyy-MM-dd HH:mm:ss");
        repoParserInfo.getFields().add(repoFieldInfo);

        repoFieldInfo = new RepositoryFieldInfoBO();
        repoFieldInfo.setName("field6");
        repoFieldInfo.setDataType(RepositoryFieldInfoBO.DataType.Boolean);
        repoFieldInfo.getProperties().put(DelimitedEntryParser.POSITION_FIELD_PROPERTY_NAME, "6");
        repoParserInfo.getFields().add(repoFieldInfo);

        // Database
        _db = MongoConnection.getInstance().getConnection();
        assertNotNull(_db);
    }

    @Before
    public void testSetup() throws Exception
    {
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        coll.drop();
    }

    @AfterClass
    public static void classTeardown() throws Exception
    {
        // Clean up old test data if any exists
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        coll.drop();
    }

    @Test
    public void testOK() throws Exception
    {
        SimpleDateFormat sf = new SimpleDateFormat(RepositoryStorageWorker.TIMESTAMP_FORMAT);
        sf.setTimeZone(TimeZone.getTimeZone(RepositoryStorageWorker.TIMESTAMP_TIMEZONE));

        // Start
        MqManager.getInstance().start();
        Repository repo = new Repository(_repoInfo);
        repo.start();
        assertEquals(Status.ONLINE, repo.getStatus());

        // Write some repository entries
        ClientSession producerSession = MqManager.getInstance().getTransactionalClientSession(REPOSITORY_NAME,
                REPOSITORY_PUBLISHER_PASSWORD);

        String queueAddress = _repoInfo.getPubSubAddress();
        ClientProducer producer = producerSession.createProducer(queueAddress);

        ClientMessage message = producerSession.createMessage(Message.TEXT_TYPE, false);
        message.putStringProperty(RepositoryStorageWorker.TIMESTAMP_PROPERTY_NAME, sf.format(new Date()));
        message.putStringProperty(RepositoryStorageWorker.SOURCE_PROPERTY_NAME, "RepositoryTest");
        message.putStringProperty(RepositoryStorageWorker.HOST_PROPERTY_NAME, "localhost");
        message.putStringProperty(RepositoryStorageWorker.SEVERITY_PROPERTY_NAME, "1");
        String entry1 = "line1|2|3|4.4|2001-5-5 5:5:5|True";
        message.getBodyBuffer().writeString(entry1);
        producer.send(message);

        message = producerSession.createMessage(Message.TEXT_TYPE, false);
        message.putStringProperty(RepositoryStorageWorker.TIMESTAMP_PROPERTY_NAME, sf.format(new Date()));
        message.putStringProperty(RepositoryStorageWorker.SOURCE_PROPERTY_NAME, "RepositoryTest");
        message.putStringProperty(RepositoryStorageWorker.HOST_PROPERTY_NAME, "localhost");
        message.putStringProperty(RepositoryStorageWorker.SEVERITY_PROPERTY_NAME, "2");
        String entry2 = "line2|2|3|4.4|2001-5-5 5:5:5|True";
        message.getBodyBuffer().writeString(entry2);
        producer.send(message);

        message = producerSession.createMessage(Message.TEXT_TYPE, false);
        message.putStringProperty(RepositoryStorageWorker.TIMESTAMP_PROPERTY_NAME, sf.format(new Date()));
        message.putStringProperty(RepositoryStorageWorker.SOURCE_PROPERTY_NAME, "RepositoryTest");
        message.putStringProperty(RepositoryStorageWorker.HOST_PROPERTY_NAME, "localhost");
        message.putStringProperty(RepositoryStorageWorker.SEVERITY_PROPERTY_NAME, "3");
        String entry3 = "line3|2|3|4.4|2001-5-5 5:5:5|True";
        message.getBodyBuffer().writeString(entry3);
        producer.send(message);

        producerSession.commit();

        // Wait for threads to process
        Thread.sleep(3000);

        // Make sure they are in the database
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        assertEquals(3, coll.find().count());

        // Stop
        repo.stop();
        assertEquals(Status.OFFLINE, repo.getStatus());
        MqManager.getInstance().stop();
    }

    @Test
    public void testUpdateRepositoryInfo() throws Exception
    {
        SimpleDateFormat sf = new SimpleDateFormat(RepositoryStorageWorker.TIMESTAMP_FORMAT);
        sf.setTimeZone(TimeZone.getTimeZone(RepositoryStorageWorker.TIMESTAMP_TIMEZONE));

        // Start
        MqManager.getInstance().start();
        Repository repo = new Repository(_repoInfo);
        repo.start();
        assertEquals(Status.ONLINE, repo.getStatus());

        // try to start again - should error
        try
        {
            repo.start();
            fail();
        }
        catch (Exception ex)
        {
            assertEquals(ChiliLogException.class, ex.getClass());
        }

        // Try to update repo - should error because it is not off line
        // Simulate we getting new repoInfo from the DB
        try
        {
            repo.setRepoInfo(_repoInfo);
            fail();
        }
        catch (Exception ex)
        {
            assertEquals(ChiliLogException.class, ex.getClass());
        }

        // Always wait for writer threads to properly start
        Thread.sleep(1000);

        // Stop
        repo.stop();
        assertEquals(Status.OFFLINE, repo.getStatus());

        // Update
        _repoInfo.setStorageQueueWorkerCount(10);
        repo.setRepoInfo(_repoInfo);

        // Restart
        repo.start();

        // Write some repository entries
        ClientSession producerSession = MqManager.getInstance().getTransactionalClientSession(REPOSITORY_NAME,
                REPOSITORY_PUBLISHER_PASSWORD);

        String queueAddress = _repoInfo.getPubSubAddress();
        ClientProducer producer = producerSession.createProducer(queueAddress);

        for (int i = 1; i <= 10000; i++)
        {
            ClientMessage message = producerSession.createMessage(Message.TEXT_TYPE, false);
            message.putStringProperty(RepositoryStorageWorker.TIMESTAMP_PROPERTY_NAME, sf.format(new Date()));
            message.putStringProperty(RepositoryStorageWorker.SOURCE_PROPERTY_NAME, "RepositoryTest");
            message.putStringProperty(RepositoryStorageWorker.HOST_PROPERTY_NAME, "localhost");
            message.putStringProperty(RepositoryStorageWorker.SEVERITY_PROPERTY_NAME, "3");
            String entry1 = "line" + i + "|2|3|4.4|2001-5-5 5:5:5|True";
            message.getBodyBuffer().writeString(entry1);
            producer.send(message);
            producerSession.commit();
        }

        // Wait for threads to process
        Thread.sleep(5000);

        // Check that threads are still running
        for (RepositoryStorageWorker rw : repo.getWriters())
        {
            assertTrue(rw.isRunning());
        }
        assertEquals(10, repo.getWriters().size());

        // Make sure that we've processed all the messages
        QueueControl qc = MqManager.getInstance().getQueueControl(repo.getRepoInfo().getPubSubAddress(),
                repo.getRepoInfo().getStorageQueueName());
        assertEquals(0, qc.getMessageCount());

        // Make sure they are in the database
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        assertEquals(10000, coll.find().count());

        // Stop
        repo.stop();
        assertEquals(Status.OFFLINE, repo.getStatus());
        MqManager.getInstance().stop();

        // Reset count
        _repoInfo.setStorageQueueWorkerCount(2);
    }

    @Test
    public void testBadEntries() throws Exception
    {
        String deadLetterAddress = AppProperties.getInstance().getMqDeadLetterAddress();
        SimpleDateFormat sf = new SimpleDateFormat(RepositoryStorageWorker.TIMESTAMP_FORMAT);
        sf.setTimeZone(TimeZone.getTimeZone(RepositoryStorageWorker.TIMESTAMP_TIMEZONE));

        // Start
        MqManager.getInstance().start();
        Repository repo = new Repository(_repoInfo);
        repo.start();
        assertEquals(Status.ONLINE, repo.getStatus());

        // Create a dead letter queue
        MqManager.getInstance().deployQueue(deadLetterAddress, "dead_letters.junit_test", false);

        // Write some repository entries
        ClientSession producerSession = MqManager.getInstance().getTransactionalClientSession(REPOSITORY_NAME,
                REPOSITORY_PUBLISHER_PASSWORD);

        String queueAddress = _repoInfo.getPubSubAddress();
        ClientProducer producer = producerSession.createProducer(queueAddress);

        // Write some good entries
        for (int i = 1; i <= 100; i++)
        {
            ClientMessage message = producerSession.createMessage(Message.TEXT_TYPE, false);
            message.putStringProperty(RepositoryStorageWorker.TIMESTAMP_PROPERTY_NAME, sf.format(new Date()));
            message.putStringProperty(RepositoryStorageWorker.SOURCE_PROPERTY_NAME, "RepositoryTest");
            message.putStringProperty(RepositoryStorageWorker.HOST_PROPERTY_NAME, "localhost");
            message.putStringProperty(RepositoryStorageWorker.SEVERITY_PROPERTY_NAME, "Debug");
            String entry1 = "line" + i + "|2|3|4.4|2001-5-5 5:5:5|True";
            if (i == 33)
            {
                entry1 = i + " - bad entry no delimiter";
            }
            message.getBodyBuffer().writeString(entry1);
            producer.send(message);
            producerSession.commit();
        }

        // Wait for threads to process
        Thread.sleep(3000);

        // Check that threads are still running
        for (RepositoryStorageWorker rw : repo.getWriters())
        {
            assertTrue(rw.isRunning());
        }
        assertEquals(2, repo.getWriters().size());

        // Make sure that bad entries have been removed from the write queue
        QueueControl qc = MqManager.getInstance().getQueueControl(repo.getRepoInfo().getPubSubAddress(),
                repo.getRepoInfo().getStorageQueueName());
        assertEquals(0, qc.getMessageCount());

        // Make sure that the bad entry ends up in the dead letter queue
        qc = MqManager.getInstance().getQueueControl(deadLetterAddress, "dead_letters.junit_test");
        assertEquals((long) 1, qc.getMessageCount());

        // Make sure that only good entries have been saved to the DB
        DBCollection coll = _db.getCollection(MONGODB_COLLECTION_NAME);
        assertEquals(99, coll.find().count());

        // Stop
        repo.stop();
        assertEquals(Status.OFFLINE, repo.getStatus());
        MqManager.getInstance().stop();

    }

    @Test
    public void testRepositoryManager() throws Exception
    {
        // Start queues
        MqManager.getInstance().start();

        // Start
        RepositoryManager.getInstance().start(true);
        Repository[] repos = RepositoryManager.getInstance().getRepositories();
        for (Repository r : repos)
        {
            if (r.getRepoInfo().getStartupStatus() == Status.ONLINE)
            {
                assertEquals(Status.ONLINE, r.getStatus());
            }
            else
            {
                assertEquals(Status.OFFLINE, r.getStatus());
            }
        }

        // Start again - should not error
        RepositoryManager.getInstance().start(true);
        Repository[] repos2 = RepositoryManager.getInstance().getRepositories();
        for (Repository r : repos2)
        {
            if (r.getRepoInfo().getStartupStatus() == Status.ONLINE)
            {
                assertEquals(Status.ONLINE, r.getStatus());
            }
            else
            {
                assertEquals(Status.OFFLINE, r.getStatus());
            }
        }
        assertEquals(repos.length, repos2.length);

        // Stop
        RepositoryManager.getInstance().stop();
        repos2 = RepositoryManager.getInstance().getRepositories();
        for (Repository r : repos2)
        {
            assertEquals(Status.OFFLINE, r.getStatus());
        }
        assertEquals(repos.length, repos2.length);

        // Stop again
        RepositoryManager.getInstance().stop();
        repos2 = RepositoryManager.getInstance().getRepositories();
        for (Repository r : repos2)
        {
            assertEquals(Status.OFFLINE, r.getStatus());
        }
        assertEquals(repos.length, repos2.length);

        // Stop queues
        MqManager.getInstance().stop();
    }
}
