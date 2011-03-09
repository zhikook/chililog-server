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

package com.chililog.server.ui.api;

import org.apache.commons.lang.StringUtils;

import com.chililog.server.common.ChiliLogException;
import com.chililog.server.data.BO;
import com.chililog.server.ui.Strings;

/**
 * API Object class contains common methods
 * 
 * @author vibul
 * 
 */
public abstract class AO
{
    /**
     * Performs optimistic locking checks
     * 
     * @param documentVersion
     *            AO object's document version
     * @param businessObject
     *            business object to check against
     * @throws ChiliLogException
     *             if the document versions differ
     */
    protected void checkOptimisticLocking(Long documentVersion, BO businessObject) throws ChiliLogException
    {
        if (businessObject.isExistingRecord() && documentVersion != businessObject.getDocumentVersion())
        {
            throw new ChiliLogException(Strings.OPTIMISTIC_LOCKING_ERROR);
        }
    }

    /**
     * Checks if a required field is present
     * 
     * @param fieldName
     *            Name of field to display if error
     * @param fieldValue
     *            Value of the field to check
     * @return fieldValue
     * @throws ChiliLogException
     *             if fieldValue is blank
     */
    protected String checkRequiredString(String fieldName, String fieldValue) throws ChiliLogException
    {
        if (StringUtils.isBlank(fieldValue))
        {
            throw new ChiliLogException(Strings.REQUIRED_FIELD_ERROR, fieldName);
        }
        return fieldName;
    }
}