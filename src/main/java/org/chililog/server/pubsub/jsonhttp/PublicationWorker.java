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

package org.chililog.server.pubsub.jsonhttp;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.chililog.server.common.AppProperties;
import org.chililog.server.common.ChiliLogException;
import org.chililog.server.common.JsonTranslator;
import org.chililog.server.common.Log4JLogger;
import org.chililog.server.data.MongoConnection;
import org.chililog.server.data.RepositoryConfigBO;
import org.chililog.server.data.RepositoryConfigController;
import org.chililog.server.data.UserBO;
import org.chililog.server.data.UserController;
import org.chililog.server.engine.RepositoryEntryMqMessage;
import org.chililog.server.pubsub.MqProducerSessionPool;
import org.chililog.server.pubsub.Strings;
import org.chililog.server.pubsub.MqProducerSessionPool.Pooled;
import org.chililog.server.workbench.workers.AuthenticationTokenAO;
import org.hornetq.api.core.Message;
import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;

import com.mongodb.DB;

/**
 * Worker to process publication requests
 */
public class PublicationWorker {

    private static Log4JLogger _logger = Log4JLogger.getLogger(PublicationWorker.class);

    private MqProducerSessionPool _sessionPool;

    /**
     * Cache for authenticated users so we don't hit the database for every publication request. Timeout is set as per
     * "mq.security_invalidation_interval" in app.properties.
     */
    private static final ConcurrentHashMap<String, Date> _authenticationCache = new ConcurrentHashMap<String, Date>();

    /**
     * Constructor
     * 
     * @param sessionPool
     *            MQ Session Pool to use to get producer for writing to an address
     */
    public PublicationWorker(MqProducerSessionPool sessionPool) {
        _sessionPool = sessionPool;
    }

    /**
     * Process a publishing request
     * 
     * @param request
     *            Publishing request in JSON format
     * @param response
     *            Publishing response in JSON format
     * @return true if successful; false if error
     */
    public boolean process(String request, StringBuilder response) {
        Pooled p = null;
        String messageId = null;
        try {
            if (StringUtils.isBlank(request)) {
                throw new IllegalArgumentException("Request content is blank.");
            }

            // Parse JSON
            PublicationRequestAO requestAO = JsonTranslator.getInstance().fromJson(request, PublicationRequestAO.class);
            messageId = requestAO.getMessageID();

            // Authenticate
            authenticate(requestAO);

            // Publish
            SimpleString repoAddress = SimpleString.toSimpleString(RepositoryConfigBO.buildPubSubAddress(requestAO
                    .getRepositoryName()));
            p = _sessionPool.getPooled();
            for (LogEntryAO logEntry : requestAO.getLogEntries()) {
                ClientMessage message = p.session.createMessage(Message.TEXT_TYPE, false);
                message.putStringProperty(RepositoryEntryMqMessage.TIMESTAMP, logEntry.getTimestamp());
                message.putStringProperty(RepositoryEntryMqMessage.SOURCE, logEntry.getSource());
                message.putStringProperty(RepositoryEntryMqMessage.HOST, logEntry.getHost());
                message.putStringProperty(RepositoryEntryMqMessage.SEVERITY, logEntry.getSeverity());
                if (!StringUtils.isBlank(logEntry.getFields())) {
                    message.putStringProperty(RepositoryEntryMqMessage.FIELDS, logEntry.getFields());
                }
                message.getBodyBuffer().writeNullableSimpleString(SimpleString.toSimpleString(logEntry.getMessage()));
                p.producer.send(repoAddress, message);
            }
            _sessionPool.returnPooled(p);

            // Prepare response
            PublicationResponseAO responseAO = new PublicationResponseAO(messageId);
            JsonTranslator.getInstance().toJson(responseAO, response);

            // Finish
            return true;
        } catch (Exception ex) {
            if (p != null) {
                try {
                    _sessionPool.addPooled();
                    p.session.close();
                } catch (Exception ex2) {
                    _logger.error(ex2, "Error closing pooled connection");
                }
            }
            _logger.error(ex, "Error processing message: %s", request);

            PublicationResponseAO responseAO = new PublicationResponseAO(messageId, ex);
            JsonTranslator.getInstance().toJson(responseAO, response);
            return false;
        }
    }

    /**
     * Authenticate request
     * 
     * @param publicationAO
     * @throws ChiliLogException
     */
    public void authenticate(PublicationRequestAO publicationAO) throws ChiliLogException {
        String repoName = publicationAO.getRepositoryName();

        // Check cache
        String key = String.format("%s_%s_%s", repoName, publicationAO.getUsername(), publicationAO.getPassword());
        Date expiry = _authenticationCache.get(key);
        if (expiry != null && expiry.after(new Date())) {
            // Validate
            return;
        }

        // Check db
        DB db = MongoConnection.getInstance().getConnection();

        // Make user repository exists
        RepositoryConfigController.getInstance().getByName(db, repoName);

        // Make sure user exists and password is valid
        UserBO user = UserController.getInstance().getByUsername(db, publicationAO.getUsername());
        boolean passwordOK = false;
        if (publicationAO.getPassword().startsWith("token:")) {
            // Password is a token so we need to check the token
            // Must have come from the workbench
            String jsonToken = publicationAO.getPassword().substring(6);
            AuthenticationTokenAO token = AuthenticationTokenAO.fromString(jsonToken);
            passwordOK = token.getUserID().equals(user.getDocumentID().toString());
        } else {
            // Make sure user exists and password is valid
            passwordOK = user.validatePassword(publicationAO.getPassword());
        }
        if (!passwordOK) {
            throw new ChiliLogException(Strings.PUBLISHER_AUTHENTICATION_ERROR);
        }

        // Make sure the user can publish to the repository
        String administratorRole = UserBO.createRepositoryAdministratorRoleName(repoName);
        String workbenchRole = UserBO.createRepositoryWorkbenchRoleName(repoName);
        String publicationRole = UserBO.createRepositoryPublisherRoleName(repoName);

        if (!user.hasRole(administratorRole) && !user.hasRole(publicationRole) && 
                !user.hasRole(workbenchRole) && !user.isSystemAdministrator()) {
            throw new ChiliLogException(Strings.PUBLISHER_AUTHENTICATION_ERROR);
        }

        // Cache details
        GregorianCalendar newExpiry = new GregorianCalendar();
        newExpiry.add(Calendar.MILLISECOND, AppProperties.getInstance().getMqSecurityInvalidationInterval());
        _authenticationCache.put(key, newExpiry.getTime());
    }
}
