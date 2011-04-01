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

package com.chililog.server.engine.parsers;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.chililog.server.common.ChiliLogException;
import com.chililog.server.data.MongoConnection;
import com.chililog.server.data.RepositoryEntryBO;
import com.chililog.server.data.RepositoryEntryBO.Severity;
import com.chililog.server.data.RepositoryEntryController;
import com.chililog.server.data.RepositoryFieldInfoBO;
import com.chililog.server.data.RepositoryInfoBO;
import com.chililog.server.data.RepositoryParserInfoBO;
import com.chililog.server.data.RepositoryParserInfoBO.AppliesTo;
import com.chililog.server.data.RepositoryParserInfoBO.ParseFieldErrorHandling;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

/**
 * Test parsing and storing delimited repository entries
 * 
 * @author vibul
 * 
 */
public class DelimitedEntryParserTest
{
    private static DB _db;

    @BeforeClass
    public static void classSetup() throws Exception
    {
        _db = MongoConnection.getInstance().getConnection();
        assertNotNull(_db);
    }

    @AfterClass
    public static void classTeardown() throws Exception
    {
        // Clean up old test data if any exists
        DBCollection coll = _db.getCollection("junit_test_repository");
        coll.drop();
    }

    @Before
    public void testSetup() throws Exception
    {
        classTeardown();
    }

    @Test
    public void testPipe() throws ChiliLogException
    {
        RepositoryInfoBO repoInfo = new RepositoryInfoBO();
        repoInfo.setName("junit_test");
        repoInfo.setDisplayName("JUnit Test 1");

        RepositoryParserInfoBO repoParserInfo = new RepositoryParserInfoBO();
        repoParserInfo.setName("parser1");
        repoParserInfo.setAppliesTo(AppliesTo.All);
        repoParserInfo.setClassName(DelimitedEntryParser.class.getName());
        repoParserInfo.setParseFieldErrorHandling(ParseFieldErrorHandling.SkipEntry);
        repoParserInfo.getProperties().put(DelimitedEntryParser.DELIMITER_PROPERTY_NAME, "|");
        repoInfo.getParsers().add(repoParserInfo);
        
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

        RepositoryEntryController c = RepositoryEntryController.getInstance(repoInfo);
        DelimitedEntryParser p = new DelimitedEntryParser(repoInfo, repoParserInfo);

        // Save Line 1 OK
        RepositoryEntryBO entry = p.parse("2001-5-5T01:02:03.001Z", "log1", "127.0.0.1", Severity.Critical.toString(), "line1|2|3|4.4|2001-5-5 5:5:5|True");
        assertNotNull(entry);
        DBObject dbObject = entry.toDBObject();
        c.save(_db, entry);

        // Get Line 1
        DBCollection coll = _db.getCollection("junit_test_repository");
        DBObject query = new BasicDBObject();
        query.put("_id", entry.toDBObject().get("_id"));
        dbObject = coll.findOne(query);

        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        cal.set(2001, 4, 5, 1, 2, 3);
        cal.set(Calendar.MILLISECOND, 1);
        
        assertNotNull(dbObject);
        assertEquals(cal.getTimeInMillis(), ((Date)dbObject.get(RepositoryEntryBO.TIMESTAMP_FIELD_NAME)).getTime());
        assertTrue(dbObject.containsField(RepositoryEntryBO.SAVED_TIMESTAMP_FIELD_NAME));
        assertEquals("line1", dbObject.get("field1"));
        assertEquals(2, dbObject.get("field2"));
        assertEquals(3L, dbObject.get("field3"));
        assertEquals(4.4d, dbObject.get("field4"));
        assertEquals(new GregorianCalendar(2001, 4, 5, 5, 5, 5).getTime(), dbObject.get("field5"));
        assertEquals(true, dbObject.get("field6"));
        assertEquals("log1", dbObject.get(RepositoryEntryBO.SOURCE_FIELD_NAME));
        assertEquals("127.0.0.1", dbObject.get(RepositoryEntryBO.HOST_FIELD_NAME));
        assertEquals(Severity.Critical.toCode(), dbObject.get(RepositoryEntryBO.SEVERITY_FIELD_NAME));
        assertEquals("line1|2|3|4.4|2001-5-5 5:5:5|True", dbObject.get(RepositoryEntryBO.MESSAGE_FIELD_NAME));

        // Save Line 2 OK
        entry = p.parse("2021-5-5T05:05:05.002Z","log1", "127.0.0.1", Severity.Debug.toString(), "line2|22|23|24.4|2021-5-5 5:5:5|xxx");
        assertNotNull(entry);
        dbObject = entry.toDBObject();
        c.save(_db, entry);

        // Get Line 2
        query = new BasicDBObject();
        query.put("_id", entry.toDBObject().get("_id"));
        dbObject = coll.findOne(query);

        cal = new GregorianCalendar();
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));
        cal.set(2021, 4, 5, 5, 5, 5);
        cal.set(Calendar.MILLISECOND, 2);
        
        assertNotNull(dbObject);
        assertEquals(cal.getTime(), dbObject.get(RepositoryEntryBO.TIMESTAMP_FIELD_NAME));
        assertTrue(dbObject.containsField(RepositoryEntryBO.SAVED_TIMESTAMP_FIELD_NAME));
        assertEquals("line2", dbObject.get("field1"));
        assertEquals(22, dbObject.get("field2"));
        assertEquals(23L, dbObject.get("field3"));
        assertEquals(24.4d, dbObject.get("field4"));
        assertEquals(new GregorianCalendar(2021, 4, 5, 5, 5, 5).getTime(), dbObject.get("field5"));
        assertEquals(false, dbObject.get("field6"));
        assertEquals("log1", dbObject.get(RepositoryEntryBO.SOURCE_FIELD_NAME));
        assertEquals("127.0.0.1", dbObject.get(RepositoryEntryBO.HOST_FIELD_NAME));
        assertEquals(Severity.Debug.toCode(), dbObject.get(RepositoryEntryBO.SEVERITY_FIELD_NAME));
        assertEquals("line2|22|23|24.4|2021-5-5 5:5:5|xxx", dbObject.get(RepositoryEntryBO.MESSAGE_FIELD_NAME));

        // Should only be 2 entries
        assertEquals(2, coll.find().count());

        // Empty ts, source, host and message is ignored
        entry = p.parse("", "log1", "127.0.0.1", Severity.Warning.toString(), "aaa");
        assertNull(entry);
        assertNotNull(p.getLastParseError());

        entry = p.parse("2021-5-5T05:05:05.000Z","", "127.0.0.1", Severity.Warning.toString(), "aaa");
        assertNull(entry);
        assertNotNull(p.getLastParseError());

        entry = p.parse("2021-5-5T05:05:05.000Z","log1", null, Severity.Warning.toString(), "aaa");
        assertNull(entry);
        assertNotNull(p.getLastParseError());

        entry = p.parse("2021-5-5T5:5:5.0Z","log1", "127.0.0.1", Severity.Warning.toString(), "");
        assertNull(entry);
        assertNotNull(p.getLastParseError());

        // Missing field
        entry = p.parse("2021-5-5T5:5:5Z","log1", "127.0.0.1", Severity.Debug.toString(), "line3");
        assertNull(entry);
        assertNotNull(p.getLastParseError());
    }

}