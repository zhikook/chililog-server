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

package org.chililog.server.engine;

import java.util.ArrayList;

import org.apache.commons.lang.NullArgumentException;
import org.chililog.server.common.AppProperties;
import org.chililog.server.common.Log4JLogger;
import org.chililog.server.data.MongoConnection;
import org.chililog.server.data.RepositoryEntryBO;
import org.chililog.server.data.RepositoryEntryController;
import org.chililog.server.data.RepositoryParserConfigBO;
import org.chililog.server.data.RepositoryParserConfigBO.AppliesTo;
import org.chililog.server.engine.parsers.EntryParser;
import org.chililog.server.engine.parsers.EntryParserFactory;
import org.hornetq.api.core.Message;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;

import com.mongodb.DB;

/**
 * <p>
 * The RepositoryStorageWorker runs as a worker thread that reading entries off the message queue, parses them and
 * writes them to mongoDB.
 * </p>
 * 
 * @author vibul
 * 
 */
public class RepositoryStorageWorker extends Thread {

    private static Log4JLogger _logger = Log4JLogger.getLogger(RepositoryStorageWorker.class);
    private Repository _repo = null;
    private String _deadLetterAddress = null;
    private boolean _stopRunning = false;
    private boolean _isRunning = false;

    private ArrayList<EntryParser> _filteredParsers = new ArrayList<EntryParser>();
    private EntryParser _catchAllParser = null;

    /**
     * 
     * Basic constructor
     * 
     * @param name
     *            name to give this thread
     * @param repo
     *            Repository that we are writing
     * @throws Exception
     *             if error
     */
    public RepositoryStorageWorker(String name, Repository repo) throws Exception {
        super(name);

        if (repo == null) {
            throw new NullArgumentException("repo");
        }
        _repo = repo;
        _deadLetterAddress = AppProperties.getInstance().getMqDeadLetterAddress();

        // Load parsers
        for (RepositoryParserConfigBO repoParserInfo : repo.getRepoConfig().getParsers()) {
            if (repoParserInfo.getAppliesTo() == AppliesTo.All) {
                _catchAllParser = EntryParserFactory.getParser(repo.getRepoConfig(), repoParserInfo);
            } else if (repoParserInfo.getAppliesTo() != AppliesTo.None) {
                _filteredParsers.add(EntryParserFactory.getParser(repo.getRepoConfig(), repoParserInfo));
            }
        }

        // If there is no catch all, then set it to the default one (that does no parsing)
        if (_catchAllParser == null) {
            _catchAllParser = EntryParserFactory.getDefaultParser(repo.getRepoConfig());
        }

        return;
    }

    /**
     * Receive incoming messages and write to the database
     */
    @Override
    public void run() {
        if (_isRunning) {
            throw new RuntimeException("RepositoryStorageWorker " + this.getName() + " is alrady running");
        }

        _logger.info("RepositoryStorageWorker '%s' started", this.getName());
        _stopRunning = false;
        _isRunning = true;
        DB db = null;
        ClientSession session = null;
        RepositoryEntryController controller = RepositoryEntryController.getInstance(_repo.getRepoConfig());

        try {
            db = MongoConnection.getInstance().getConnection();

            session = MqService.getInstance().getTransactionalSystemClientSession();
            ClientConsumer messageConsumer = session.createConsumer(_repo.getRepoConfig().getStorageQueueName());
            session.start();

            ClientProducer dlqProducer = (_deadLetterAddress == null ? null : session
                    .createProducer(_deadLetterAddress));

            while (true) {
                // Wait (sleep) 1/2 second for messages
                ClientMessage messageReceived = messageConsumer.receive(500);
                if (messageReceived != null) {
                    try {
                        String ts = messageReceived.getStringProperty(RepositoryEntryMqMessage.TIMESTAMP);
                        String source = messageReceived.getStringProperty(RepositoryEntryMqMessage.SOURCE);
                        String host = messageReceived.getStringProperty(RepositoryEntryMqMessage.HOST);
                        String severity = messageReceived.getStringProperty(RepositoryEntryMqMessage.SEVERITY);
                        String fields = messageReceived.getStringProperty(RepositoryEntryMqMessage.FIELDS);
                        SimpleString messageSimpleString = messageReceived.getBodyBuffer().readNullableSimpleString();
                        String message = "";
                        if (messageSimpleString != null) {
                            message = messageSimpleString.toString();
                        }

                        // Parse message
                        EntryParser entryParser = getParser(source, host);
                        RepositoryEntryBO repoEntry = entryParser.parse(ts, source, host, severity, fields, message);

                        // Save message
                        if (repoEntry != null) {
                            controller.save(db, repoEntry);
                            _logger.debug("RepositoryStorageWorker '%s' processed message id %s: %s", this.getName(),
                                    messageReceived.getMessageID(), message);
                        }

                        // Commit message so that we remove it form the queue
                        messageReceived.acknowledge();
                        session.commit();

                        // Message could not be parsed so add to dead letter queue
                        // Do it after session.commit() because adding to DLA requires another commit
                        // and we want to flag the original message as having been processed so it is not re-processed
                        if (repoEntry == null) {
                            // Cannot parse. Commit and add to Dead Letter Queue with the error
                            _logger.error("RepositoryStorageWorker '%s' parse error processing message id %s: '%s'. "
                                    + "Moved message to dead letter queue.", this.getName(),
                                    messageReceived.getMessageID(), message);

                            addToDeadLetterQueue(session, dlqProducer, message, entryParser.getLastParseError());
                        }
                    } catch (Exception ex) {
                        // This exception really should only be for mongoDB write errors

                        // Rollback and try delivery again (just in case we have bad DB connection or other)
                        // We want to ack the message so that we don't get in an endless try again loop
                        // Without ack, message delivery count does not get incremented!
                        messageReceived.acknowledge();
                        session.rollback();

                        String msg = null;
                        msg = "This is delivery attempt # " + messageReceived.getDeliveryCount();
                        _logger.error(ex, "RepositoryStorageWorker '%s' processing error. %s. %s", this.getName(),
                                ex.getMessage(), msg);
                    }
                }

                // See if we want to quit
                if (_stopRunning) {
                    break;
                }

                // Loop again
            }

            // We are done
            _logger.info("RepositoryStorageWorker '%s' stopped", this.getName());
            return;
        } catch (Exception ex) {
            // Just log and terminate
            // TODO Repository or some class should have a periodic check to make sure this thread is started again in
            // the event of an exception stopping the thread
            _logger.error(ex, "RepositoryStorageWorker '%s' error. %s", this.getName(), ex.getMessage());
        } finally {
            _isRunning = false;
            MqService.getInstance().closeClientSession(session);
        }
    }

    /**
     * Figure out which parser to use
     * 
     * @param source
     *            Application or service that created the log entry
     * @param host
     *            Device or machine name or IP address that the source is running on
     * @return Entry parser to use. Null if no parser found.
     */
    private EntryParser getParser(String source, String host) {
        for (EntryParser p : _filteredParsers) {
            if (p.isApplicable(source, host)) {
                return p;
            }
        }

        return _catchAllParser;
    }

    /**
     * Write a message to the dead letter queue
     * 
     * @param session
     * @param dlqProducer
     * @param textEntry
     * @param ex
     */
    private void addToDeadLetterQueue(ClientSession session, ClientProducer dlqProducer, String textEntry, Exception ex) {
        try {
            if (dlqProducer == null) {
                return;
            }

            ClientMessage message = session.createMessage(Message.TEXT_TYPE, false);

            // Special property to identify original address
            // See http://docs.jboss.org/hornetq/2.2.2.Final/user-manual/en/html_single/index.html#d0e4430
            message.putStringProperty("_HQ_ORIG_ADDRESS", _repo.getRepoConfig().getPubSubAddress());

            message.putStringProperty("RepositoryStorageWorker", this.getName());
            message.putStringProperty("ParseException", ex.toString());
            message.getBodyBuffer().writeString(textEntry);
            dlqProducer.send(message);
            session.commit();
        } catch (Exception ex2) {
            // Just log can continue
            _logger.error(ex2, "RepositoryStorageWorker '%s' could not add message to dead letter queue error. %s",
                    this.getName(), ex2.getMessage());
        }
    }

    /**
     * Returns the repository to which this thread belongs
     */
    public Repository getRepository() {
        return _repo;
    }

    /**
     * Returns true if this thread is running, false if not
     */
    public boolean isRunning() {
        return _isRunning;
    }

    /**
     * Make this thread stop running
     */
    public void stopRunning() {
        _stopRunning = true;
    }

}
