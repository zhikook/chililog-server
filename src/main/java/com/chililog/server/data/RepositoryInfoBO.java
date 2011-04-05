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

package com.chililog.server.data;

import java.io.Serializable;
import java.util.ArrayList;

import com.chililog.server.common.ChiliLogException;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * <p>
 * This class contains information that describes a repository
 * </p>
 * 
 * @author vibul
 * 
 */
public class RepositoryInfoBO extends BO implements Serializable
{
    private static final long serialVersionUID = 1L;

    private String _name;
    private String _displayName;
    private String _description;
    private Status _startupStatus = Status.ONLINE;
    private boolean _readQueueDurable = false;
    private boolean _writeQueueDurable = false;
    private long _writeQueueWorkerCount = 1;
    private long _writeQueueMaxMemory = 1024 * 1024 * 20; // 20 MB
    private QueueMaxMemoryPolicy _writeQueueMaxMemoryPolicy = QueueMaxMemoryPolicy.PAGE;
    private long _writeQueuePageSize = 1024 * 1024 * 4; // MB
    private long _maxKeywords = -1;
    private ArrayList<RepositoryParserInfoBO> _parsers = new ArrayList<RepositoryParserInfoBO>();

    static final String NAME_FIELD_NAME = "name";
    static final String DISPLAY_NAME_FIELD_NAME = "display_name";
    static final String DESCRIPTION_FIELD_NAME = "description";
    static final String STARTUP_STATUS_FIELD_NAME = "startup_status";
    static final String DURABLE_READ_QUEUE_FIELD_NAME = "is_read_queue_durable";
    static final String WRITE_QUEUE_DURABLE_FIELD_NAME = "is_write_queue_durable";
    static final String WRITE_QUEUE_WORKER_COUNT_FIELD_NAME = "write_queue_worker_count";
    static final String WRITE_QUEUE_MAX_MEMORY_FIELD_NAME = "write_queue_max_memory";
    static final String WRITE_QUEUE_MAX_MEMORY_POLICY_FIELD_NAME = "write_queue_max_memory_policy";
    static final String WRITE_QUEUE_PAGE_SIZE_FIELD_NAME = "write_queue_page_size";
    static final String MAX_KEYWORDS = "max_keywords";
    static final String PARSERS_FIELD_NAME = "parsers";

    public static final long MAX_KEYWORDS_UNLIMITED = -1;

    /**
     * Basic constructor
     */
    public RepositoryInfoBO()
    {
        return;
    }

    /**
     * Constructor that loads our properties retrieved from the mongoDB dbObject
     * 
     * @param dbObject
     *            database object as retrieved from mongoDB
     * @throws ChiliLogException
     */
    public RepositoryInfoBO(DBObject dbObject) throws ChiliLogException
    {
        super(dbObject);
        _name = MongoUtils.getString(dbObject, NAME_FIELD_NAME, true);
        _displayName = MongoUtils.getString(dbObject, DISPLAY_NAME_FIELD_NAME, false);
        _description = MongoUtils.getString(dbObject, DESCRIPTION_FIELD_NAME, false);
        _startupStatus = Status.valueOf(MongoUtils.getString(dbObject, STARTUP_STATUS_FIELD_NAME, true));

        _readQueueDurable = MongoUtils.getBoolean(dbObject, DURABLE_READ_QUEUE_FIELD_NAME, true);

        _writeQueueDurable = MongoUtils.getBoolean(dbObject, WRITE_QUEUE_DURABLE_FIELD_NAME, true);
        _writeQueueWorkerCount = MongoUtils.getLong(dbObject, WRITE_QUEUE_WORKER_COUNT_FIELD_NAME, true);
        _writeQueueMaxMemory = MongoUtils.getLong(dbObject, WRITE_QUEUE_MAX_MEMORY_FIELD_NAME, true);
        _writeQueueMaxMemoryPolicy = QueueMaxMemoryPolicy.valueOf(MongoUtils.getString(dbObject,
                WRITE_QUEUE_MAX_MEMORY_POLICY_FIELD_NAME, true));
        _writeQueuePageSize = MongoUtils.getLong(dbObject, WRITE_QUEUE_PAGE_SIZE_FIELD_NAME, true);

        _maxKeywords = MongoUtils.getLong(dbObject, MAX_KEYWORDS, true);

        BasicDBList list = (BasicDBList) dbObject.get(PARSERS_FIELD_NAME);
        ArrayList<RepositoryParserInfoBO> parserList = new ArrayList<RepositoryParserInfoBO>();
        if (list != null && list.size() > 0)
        {
            for (Object item : list)
            {
                RepositoryParserInfoBO field = new RepositoryParserInfoBO((DBObject) item);
                parserList.add(field);
            }
        }
        _parsers = parserList;

        return;
    }

    /**
     * Puts our properties into the mongoDB object so that it can be saved
     * 
     * @param dbObject
     *            mongoDB database object that can be used for saving
     * @throws ChiliLogException
     */
    @Override
    protected void savePropertiesToDBObject(DBObject dbObject) throws ChiliLogException
    {
        MongoUtils.setString(dbObject, NAME_FIELD_NAME, _name, true);
        MongoUtils.setString(dbObject, DISPLAY_NAME_FIELD_NAME, _displayName, false);
        MongoUtils.setString(dbObject, DESCRIPTION_FIELD_NAME, _description, false);
        MongoUtils.setString(dbObject, STARTUP_STATUS_FIELD_NAME, _startupStatus.toString(), true);

        MongoUtils.setBoolean(dbObject, DURABLE_READ_QUEUE_FIELD_NAME, _readQueueDurable, true);

        MongoUtils.setBoolean(dbObject, WRITE_QUEUE_DURABLE_FIELD_NAME, _writeQueueDurable, true);
        MongoUtils.setLong(dbObject, WRITE_QUEUE_WORKER_COUNT_FIELD_NAME, _writeQueueWorkerCount, true);
        MongoUtils.setLong(dbObject, WRITE_QUEUE_MAX_MEMORY_FIELD_NAME, _writeQueueMaxMemory, true);
        MongoUtils.setString(dbObject, WRITE_QUEUE_MAX_MEMORY_POLICY_FIELD_NAME, _writeQueueMaxMemoryPolicy.toString(),
                true);
        MongoUtils.setLong(dbObject, WRITE_QUEUE_PAGE_SIZE_FIELD_NAME, _writeQueuePageSize, true);

        MongoUtils.setLong(dbObject, MAX_KEYWORDS, _maxKeywords, true);

        ArrayList<DBObject> fieldList = new ArrayList<DBObject>();
        for (RepositoryParserInfoBO parser : _parsers)
        {
            BasicDBObject obj = new BasicDBObject();
            parser.savePropertiesToDBObject(obj);
            fieldList.add(obj);
        }
        dbObject.put(PARSERS_FIELD_NAME, fieldList);
    }

    /**
     * <p>
     * Returns the unique name for this repository. This name forms part the mongoDB collection for storing data for
     * this repository.
     * </p>
     * <p>
     * If the name is "xxx", then the mongoDB collection name is "xxx_repository"
     * </p>
     */
    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    /**
     * Returns the name of the collection in mongoDB where repository entries will be stored.
     */
    public String getMongoDBCollectionName()
    {
        return String.format("%s_repository", _name);
    }

    /**
     * Returns user friendly display name for this repository
     */
    public String getDisplayName()
    {
        return _displayName;
    }

    public void setDisplayName(String displayName)
    {
        _displayName = displayName;
    }

    /**
     * Returns the description for this repository
     */
    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    /**
     * Returns the status of the repository
     */
    public Status getStartupStatus()
    {
        return _startupStatus;
    }

    public void setStartupStatus(Status status)
    {
        _startupStatus = status;
    }

    /**
     * Returns a list fields that is to be parsed and stored in this repository
     */
    public ArrayList<RepositoryParserInfoBO> getParsers()
    {
        return _parsers;
    }

    /**
     * Returns the address to use for the message queue that handles dead letters
     */
    public String getDeadLetterAddress()
    {
        return String.format("repository.%s.dead_letters", _name);
    }

    /**
     * Returns the address to use for the message queue that handles incoming entries
     */
    public String getWriteQueueAddress()
    {
        return String.format("repository.%s.write", _name);
    }

    /**
     * Returns the name of the role in which a user must be a member before permission is granted to write (produce)
     * incoming entries to this repository
     */
    public String getWriteQueueRole()
    {
        return String.format("repository.%s.writer", _name);
    }

    /**
     * Returns the address to use for the message queue that handles outgoing entries
     */
    public String getReadQueueAddress()
    {
        return String.format("repository.%s.read", _name);
    }

    /**
     * Returns the name of the role in which a user must be a member before permission is granted to read (consume)
     * outgoing entries to this repository
     */
    public String getReadQueueRole()
    {
        return String.format("repository.%s.reader", _name);
    }

    /**
     * Returns a flag indicating if the read queue for this repository is to be durable. For this to take effect, the
     * app.properties mq.persistence_enabled must also be set to true.
     */
    public boolean isReadQueueDurable()
    {
        return _readQueueDurable;
    }

    public void setReadQueueDurable(boolean durableReadQueue)
    {
        _readQueueDurable = durableReadQueue;
    }

    /**
     * Returns a flag indicating if the write queue for this repository is to be durable. For this to take effect, the
     * app.properties mq.persistence_enabled must also be set to true.
     */
    public boolean isWriteQueueDurable()
    {
        return _writeQueueDurable;
    }

    public void setWriteQueueDurable(boolean durableWriteQueue)
    {
        _writeQueueDurable = durableWriteQueue;
    }

    /**
     * Returns the number of writer worker threads that will be created to processing incoming entries.
     */
    public long getWriteQueueWorkerCount()
    {
        return _writeQueueWorkerCount;
    }

    public void setWriteQueueWorkerCount(long writeWorkerCount)
    {
        _writeQueueWorkerCount = writeWorkerCount;
    }

    /**
     * The maximum amount of memory (in bytes) that will be used by this queue. <code>-1</code> means no limit.
     */
    public long getWriteQueueMaxMemory()
    {
        return _writeQueueMaxMemory;
    }

    public void setWriteQueueMaxMemory(long writeQueueMaxMemory)
    {
        _writeQueueMaxMemory = writeQueueMaxMemory;
    }

    /**
     * Determines what happens when WriteQueueMaxMemory is reached.
     */
    public QueueMaxMemoryPolicy getWriteQueueMaxMemoryPolicy()
    {
        return _writeQueueMaxMemoryPolicy;
    }

    public void setWriteQueueMaxMemoryPolicy(QueueMaxMemoryPolicy writeQueueMaxMemoryPolicy)
    {
        _writeQueueMaxMemoryPolicy = writeQueueMaxMemoryPolicy;
    }

    /**
     * If WriteQueueMaxMemoryPolicy is set to PAGE, then this value determines the size of each page file on the hard
     * disk in bytes.
     */
    public long getWriteQueuePageSize()
    {
        return _writeQueuePageSize;
    }

    public void setWriteQueuePageSize(long writeQueuePageSize)
    {
        _writeQueuePageSize = writeQueuePageSize;
    }

    /**
     * Maximum number of keywords to be stored per entry
     */
    public long getMaxKeywords()
    {
        return _maxKeywords;
    }

    public void setMaxKeywords(long maxKeywords)
    {
        _maxKeywords = maxKeywords;
    }

    /**
     * Repository status
     * 
     * @author vibul
     * 
     */
    public enum Status
    {
        /**
         * Users with permission can read from and write to this repository
         */
        ONLINE,

        /**
         * Nobody can read from or write to this repository
         */
        OFFLINE
    }

    /**
     * Policy to follow once a queue's max memory is reached
     * 
     * @author vibul
     * 
     */
    public enum QueueMaxMemoryPolicy
    {
        /**
         * Messages will be pushed to page files on the hard disk
         */
        PAGE,

        /**
         * Old messages will be dropped
         */
        DROP,

        /**
         * Force producers to block and wait before new messages can be sent
         */
        BLOCK
    }
}
