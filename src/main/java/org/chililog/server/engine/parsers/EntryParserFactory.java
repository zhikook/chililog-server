//
// Copyright 2010 Cinch Logic Pty Ltd
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

package org.chililog.server.engine.parsers;

import org.apache.commons.lang.ClassUtils;
import org.apache.commons.lang.NullArgumentException;
import org.apache.commons.lang.reflect.ConstructorUtils;
import org.chililog.server.common.ChiliLogException;
import org.chililog.server.data.RepositoryConfigBO;
import org.chililog.server.data.RepositoryParserConfigBO;
import org.chililog.server.engine.Strings;

/**
 * Factory to instance entry parsers
 * 
 * @author vibul
 * 
 */
public class EntryParserFactory {

    private static final String _delimitedEntryParserClassName = DelimitedEntryParser.class.getName();
    private static final String _jsonEntryParserClassName = JsonEntryParser.class.getName();
    private static final String _regexEntryParserClassName = RegexEntryParser.class.getName();

    /**
     * Instances the correct entry parser
     * 
     * @param repoInfo
     *            Repository meta data
     * @param repoParserInfo
     *            Parser meta data
     * @return Entry parser
     * @throws ChiliLogException
     */
    public static EntryParser getParser(RepositoryConfigBO repoInfo, RepositoryParserConfigBO repoParserInfo)
            throws ChiliLogException {
        if (repoParserInfo == null) {
            throw new NullArgumentException("repoParserInfo");
        }

        try {
            String className = repoParserInfo.getClassName();
            if (className.equals(_delimitedEntryParserClassName)) {
                return new DelimitedEntryParser(repoInfo, repoParserInfo);
            } else if (className.equals(_regexEntryParserClassName)) {
                return new RegexEntryParser(repoInfo, repoParserInfo);
            } else if (className.equals(_jsonEntryParserClassName)) {
                return new JsonEntryParser(repoInfo, repoParserInfo);
            }

            // Use reflection to instance it
            Class<?> cls = ClassUtils.getClass(className);
            return (EntryParser) ConstructorUtils.invokeConstructor(cls, new Object[] { repoInfo, repoParserInfo });
        } catch (Exception ex) {
            throw new ChiliLogException(ex, Strings.PARSER_FACTORY_ERROR, repoParserInfo.getName(), repoInfo.getName(),
                    ex.getMessage());
        }
    }

    /**
     * Instances the correct entry parser
     * 
     * @return Entry parser
     * @throws ChiliLogException
     */
    public static EntryParser getDefaultParser(RepositoryConfigBO repoInfo) throws ChiliLogException {
        RepositoryParserConfigBO parserInfo = new RepositoryParserConfigBO();
        parserInfo.setName("Default");
        return new DefaultEntryParser(repoInfo, parserInfo);
    }
}
