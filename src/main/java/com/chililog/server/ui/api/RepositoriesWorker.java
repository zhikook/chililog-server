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

import java.util.ArrayList;
import java.util.List;

import javax.naming.OperationNotSupportedException;

import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import com.chililog.server.common.ChiliLogException;
import com.chililog.server.data.MongoConnection;
import com.chililog.server.data.MongoJsonSerializer;
import com.chililog.server.data.RepositoryEntryController;
import com.chililog.server.data.RepositoryListCriteria;
import com.chililog.server.data.RepositoryListCriteria.QueryType;
import com.chililog.server.engine.Repository;
import com.chililog.server.engine.RepositoryManager;
import com.chililog.server.ui.Strings;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;

/**
 * <p>
 * Repositories worker provides the following services:
 * <ul>
 * <li>start all - HTTP POST /api/repositories?action=start</li>
 * <li>start one - HTTP POST /api/repositories/{id}?action=start</li>
 * <li>stop all - HTTP POST /api/repositories?action=stop</li>
 * <li>stop one - HTTP POST /api/repositories/{id}?action=stop</li>
 * <li>reload all - HTTP POST /api/repositories?action=reload</li>
 * <li>read all - HTTP GET /api/repositories</li>
 * <li>read one - HTTP GET /api/repositories/{id}</li>
 * <li>read entry - HTTP GET /api/repositories/{id}/entries?query_type=find</li>
 * </p>
 */
public class RepositoriesWorker extends Worker
{
    public static final String ACTION_URI_QUERYSTRING_PARAMETER_NAME = "action";
    public static final String START_OPERATION = "start";
    public static final String STOP_OPERATION = "stop";
    public static final String RELOAD_OPERATION = "reload";

    public static final String ENTRY_QUERY_TYPE_URI_QUERYSTRING_PARAMETER_NAME = "query_type";
    public static final String ENTRY_QUERY_FIELDS_URI_QUERYSTRING_PARAMETER_NAME = "fields";
    public static final String ENTRY_QUERY_CONDITIONS_URI_QUERYSTRING_PARAMETER_NAME = "conditions";
    public static final String ENTRY_QUERY_ORDER_BY_URI_QUERYSTRING_PARAMETER_NAME = "orderBy";
    public static final String ENTRY_QUERY_INITIAL_URI_QUERYSTRING_PARAMETER_NAME = "initial";
    public static final String ENTRY_QUERY_REDUCE_URI_QUERYSTRING_PARAMETER_NAME = "reduce";
    public static final String ENTRY_QUERY_FINALIZE_URI_QUERYSTRING_PARAMETER_NAME = "finalize";

    public static final String ENTRY_QUERY_TYPE_HEADER_NAME = "X-ChiliLog-QueryType";
    public static final String ENTRY_QUERY_FIELDS_HEADER_NAME = "X-ChiliLog-Fields";
    public static final String ENTRY_QUERY_CONDITIONS_HEADER_NAME = "X-ChiliLog-Conditions";
    public static final String ENTRY_QUERY_ORDER_BY_HEADER_NAME = "X-ChiliLog-OrderBy";
    public static final String ENTRY_QUERY_INITIAL_HEADER_NAME = "X-ChiliLog-Initial";
    public static final String ENTRY_QUERY_REDUCE_HEADER_NAME = "X-ChiliLog-Reduce";
    public static final String ENTRY_QUERY_FINALIZE_HEADER_NAME = "X-ChiliLog-Finalize";

    /**
     * Constructor
     */
    public RepositoriesWorker(HttpRequest request)
    {
        super(request);
        return;
    }

    /**
     * Can only create and delete sessions
     */
    @Override
    public HttpMethod[] getSupportedMethods()
    {
        return new HttpMethod[]
        { HttpMethod.GET, HttpMethod.POST };
    }

    /**
     * Create
     * 
     * @throws Exception
     */
    @Override
    public ApiResult processPost(Object requestContent) throws Exception
    {
        try
        {
            String action = this.getUriQueryStringParameter(ACTION_URI_QUERYSTRING_PARAMETER_NAME, false);
            if (this.getUriPathParameters() == null || this.getUriPathParameters().length == 0)
            {
                if (action.equalsIgnoreCase(START_OPERATION))
                {
                    RepositoryManager.getInstance().start();
                }
                else if (action.equalsIgnoreCase(STOP_OPERATION))
                {
                    RepositoryManager.getInstance().stop();
                }
                else if (action.equalsIgnoreCase(RELOAD_OPERATION))
                {
                    RepositoryManager.getInstance().loadRepositories();
                }
                else
                {
                    throw new UnsupportedOperationException(String.format("Action '%s' not supported.", action));
                }
            }
            else
            {
                String id = this.getUriPathParameters()[ID_URI_PATH_PARAMETER_INDEX];
                Repository repo = RepositoryManager.getInstance().getRepository(id);

                if (action.equalsIgnoreCase(START_OPERATION))
                {
                    repo.start();
                }
                else if (action.equalsIgnoreCase(STOP_OPERATION))
                {
                    repo.stop();
                }
                else
                {
                    throw new UnsupportedOperationException(String.format("Action '%s' not supported.", action));
                }
            }

            // Return response
            return new ApiResult(this.getAuthenticationToken(), null, null);
        }
        catch (Exception ex)
        {
            return new ApiResult(HttpResponseStatus.BAD_REQUEST, ex);
        }
    }

    /**
     * Read
     * 
     * @throws Exception
     */
    @SuppressWarnings("rawtypes")
    @Override
    public ApiResult processGet() throws Exception
    {
        try
        {
            DB db = MongoConnection.getInstance().getConnection();
            Object responseContent = null;

            // Get info on all repositories
            // HTTP GET /api/repositories
            if (this.getUriPathParameters() == null || this.getUriPathParameters().length == 0)
            {
                Repository[] list = RepositoryManager.getInstance().getRepositories();
                if (list != null && list.length > 0)
                {
                    ArrayList<RepositoryAO> aoList = new ArrayList<RepositoryAO>();
                    for (Repository repo : list)
                    {
                        if (isAuthenticatedUserAdministrator()
                                || this.getAuthenticatedUser().hasRole(repo.getRepoInfo().getReadQueueRole()))
                        {
                            aoList.add(new RepositoryAO(repo));
                        }
                    }

                    if (!aoList.isEmpty())
                    {
                        responseContent = aoList.toArray(new RepositoryAO[] {});
                    }
                }
            }
            else if (this.getUriPathParameters().length == 1)
            {
                // Get info on specified repository
                // HTTP GET /api/repositories/{id}
                String id = this.getUriPathParameters()[ID_URI_PATH_PARAMETER_INDEX];
                Repository repo = RepositoryManager.getInstance().getRepository(id);
                if (repo != null
                        && (isAuthenticatedUserAdministrator() || this.getAuthenticatedUser().hasRole(
                                repo.getRepoInfo().getReadQueueRole())))
                {
                    responseContent = new RepositoryAO(repo);
                }
                else
                {
                    // Assume not found
                    throw new ChiliLogException(Strings.REPOSITORY_NOT_FOUND_ERROR, id);
                }
            }
            else if (this.getUriPathParameters().length == 2)
            {
                // HTTP GET /api/repositories/{id}/entries?query_type=find
                // Get entries for a specific repository
                String id = this.getUriPathParameters()[ID_URI_PATH_PARAMETER_INDEX];
                Repository repo = RepositoryManager.getInstance().getRepository(id);

                if (repo == null
                        || (!isAuthenticatedUserAdministrator() && !this.getAuthenticatedUser().hasRole(
                                repo.getRepoInfo().getReadQueueRole())))
                {
                    // Assume not found
                    throw new ChiliLogException(Strings.REPOSITORY_NOT_FOUND_ERROR, id);
                }

                // Load criteria
                QueryType queryType = Enum.valueOf(QueryType.class,
                        this.getUriQueryStringParameter(ENTRY_QUERY_TYPE_URI_QUERYSTRING_PARAMETER_NAME, false)
                                .toUpperCase());
                RepositoryListCriteria criteria = loadCriteria();

                // Convert to JSON ourselves because this is not a simple AO object.
                // mongoDB object JSON serialization required
                StringBuilder json = new StringBuilder();

                // Get controller and execute query
                RepositoryEntryController controller = RepositoryEntryController.getInstance(repo.getRepoInfo());
                if (queryType == QueryType.FIND)
                {
                    ArrayList<DBObject> list = controller.executeFindQuery(db, criteria);

                    if (list != null && !list.isEmpty())
                    {
                        MongoJsonSerializer.serialize(new BasicDBObject("find", list), json);
                    }
                }
                else if (queryType == QueryType.COUNT)
                {
                    int count = controller.executeCountQuery(db, criteria);
                    MongoJsonSerializer.serialize(new BasicDBObject("count", count), json);
                }
                else if (queryType == QueryType.DISTINCT)
                {
                    List l = controller.executeDistinctQuery(db, criteria);
                    MongoJsonSerializer.serialize(new BasicDBObject("distinct", l), json);
                }
                else if (queryType == QueryType.GROUP)
                {
                    DBObject groupObject = controller.executeGroupQuery(db, criteria);
                    MongoJsonSerializer.serialize(new BasicDBObject("group", groupObject), json);
                }
                else
                {
                    throw new OperationNotSupportedException("Unsupported query type: " + queryType.toString());
                }

                // If there is no json, skip this and a 204 No Content will be returned
                if (json.length() > 0)
                {
                    responseContent = json.toString().getBytes(Worker.JSON_CHARSET);
                    ApiResult result = new ApiResult(this.getAuthenticationToken(), JSON_CONTENT_TYPE, responseContent);

                    if (criteria.getDoPageCount())
                    {
                        result.getHeaders().put(PAGE_COUNT_HEADER, new Integer(criteria.getPageCount()).toString());
                    }
                    return result;
                }
            }

            // Return response
            return new ApiResult(this.getAuthenticationToken(), JSON_CONTENT_TYPE, responseContent);
        }
        catch (Exception ex)
        {
            return new ApiResult(HttpResponseStatus.BAD_REQUEST, ex);
        }
    }

    /**
     * Load our criteria from query string and headers (in case it is too big for query string)
     * 
     * @returns query criteria
     * @throws ChiliLogException
     */
    private RepositoryListCriteria loadCriteria() throws ChiliLogException
    {
        HttpRequest request = this.getRequest();
        String s;

        RepositoryListCriteria criteria = new RepositoryListCriteria();
        this.loadBaseListCriteriaParameters(criteria);

        criteria.setFields(this.getUriQueryStringParameter(ENTRY_QUERY_FIELDS_URI_QUERYSTRING_PARAMETER_NAME, true));
        s = request.getHeader(ENTRY_QUERY_FIELDS_HEADER_NAME);
        if (!StringUtils.isBlank(s))
        {
            criteria.setFields(s);
        }

        criteria.setConditions(this.getUriQueryStringParameter(ENTRY_QUERY_CONDITIONS_URI_QUERYSTRING_PARAMETER_NAME,
                true));
        s = request.getHeader(ENTRY_QUERY_CONDITIONS_HEADER_NAME);
        if (!StringUtils.isBlank(s))
        {
            criteria.setConditions(s);
        }

        criteria.setOrderBy(this.getUriQueryStringParameter(ENTRY_QUERY_ORDER_BY_URI_QUERYSTRING_PARAMETER_NAME, true));
        s = request.getHeader(ENTRY_QUERY_ORDER_BY_HEADER_NAME);
        if (!StringUtils.isBlank(s))
        {
            criteria.setOrderBy(s);
        }

        criteria.setInitial(this.getUriQueryStringParameter(ENTRY_QUERY_INITIAL_URI_QUERYSTRING_PARAMETER_NAME, true));
        s = request.getHeader(ENTRY_QUERY_INITIAL_HEADER_NAME);
        if (!StringUtils.isBlank(s))
        {
            criteria.setInitial(s);
        }

        criteria.setReduceFunction(this.getUriQueryStringParameter(ENTRY_QUERY_REDUCE_URI_QUERYSTRING_PARAMETER_NAME,
                true));
        s = request.getHeader(ENTRY_QUERY_REDUCE_HEADER_NAME);
        if (!StringUtils.isBlank(s))
        {
            criteria.setReduceFunction(s);
        }

        criteria.setFinalizeFunction(this.getUriQueryStringParameter(
                ENTRY_QUERY_FINALIZE_URI_QUERYSTRING_PARAMETER_NAME, true));
        s = request.getHeader(ENTRY_QUERY_FINALIZE_HEADER_NAME);
        if (!StringUtils.isBlank(s))
        {
            criteria.setFinalizeFunction(s);
        }

        return criteria;
    }
}