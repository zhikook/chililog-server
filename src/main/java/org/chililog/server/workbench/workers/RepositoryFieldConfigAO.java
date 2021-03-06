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

package org.chililog.server.workbench.workers;

import java.util.ArrayList;
import java.util.Map.Entry;

import org.chililog.server.common.ChiliLogException;
import org.chililog.server.data.RepositoryFieldConfigBO;
import org.chililog.server.data.RepositoryFieldConfigBO.DataType;

/**
 * <p>
 * Repository Field API Object
 * </p>
 * 
 * @author vibul
 * 
 */
public class RepositoryFieldConfigAO extends AO {

    private String _name;
    private String _displayName;
    private String _description;
    private DataType _dataType;
    private RepositoryPropertyConfigAO[] _properties = null;

    /**
     * Basic constructor
     */
    public RepositoryFieldConfigAO() {
        return;
    }

    /**
     * Constructor that copies properties form the business object
     * 
     * @param repoFieldConfig
     *            Repository info business object
     */
    public RepositoryFieldConfigAO(RepositoryFieldConfigBO repoFieldConfig) {
        _name = repoFieldConfig.getName();
        _displayName = repoFieldConfig.getDisplayName();
        _description = repoFieldConfig.getDescription();
        _dataType = repoFieldConfig.getDataType();

        if (repoFieldConfig.getProperties() == null || repoFieldConfig.getProperties().isEmpty()) {
            _properties = null;
        } else {
            ArrayList<RepositoryPropertyConfigAO> propertyList = new ArrayList<RepositoryPropertyConfigAO>();
            for (Entry<String, String> e : repoFieldConfig.getProperties().entrySet()) {
                propertyList.add(new RepositoryPropertyConfigAO(e.getKey(), e.getValue()));
            }
            _properties = propertyList.toArray(new RepositoryPropertyConfigAO[] {});
        }

        return;
    }

    /**
     * Updates the supplied business object with info from this api object
     * 
     * @param repoFieldConfig
     *            business object to update
     * @throws ChiliLogException
     */
    public void toBO(RepositoryFieldConfigBO repoFieldConfig) throws ChiliLogException {
        repoFieldConfig.setName(checkRequiredField("Field Name", _name));
        repoFieldConfig.setDisplayName(_displayName);
        repoFieldConfig.setDescription(_description);
        repoFieldConfig.setDataType(_dataType);

        repoFieldConfig.getProperties().clear();
        if (_properties != null && _properties.length > 0) {
            for (RepositoryPropertyConfigAO property : _properties) {
                repoFieldConfig.getProperties().put(property.getKey(), property.getValue());
            }
        }

        return;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String displayName) {
        _displayName = displayName;
    }

    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
    }

    public DataType getDataType() {
        return _dataType;
    }

    public void setDataType(DataType dataType) {
        _dataType = dataType;
    }

    public RepositoryPropertyConfigAO[] getProperties() {
        return _properties;
    }

    public void setProperties(RepositoryPropertyConfigAO[] properties) {
        _properties = properties;
    }
}
