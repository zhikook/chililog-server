// ==========================================================================
// Project:   Chililog
// Copyright: ©2011 My Company, Inc.
// ==========================================================================

sc_require('controllers/data_controller_mixin');


/** @class

  Manages repository information records and keeps them in sync with the server

 @extends SC.Object
 */
Chililog.repositoryInfoDataController = SC.ObjectController.create(Chililog.DataControllerMixin,
/** @scope Chililog.userDataController.prototype */ {

  /**
   * YEs if we are performing a server synchronization
   * @type Boolean
   */
  isSynchronizingWithServer: NO,

  /**
   * Synchronize data in the store with the data on the server
   * We sync repository status after we get all the repository info
   *
   * @param {Boolean} clearLocalData YES will delete data from local store before loading.
   * @param {Object} [callbackTarget] Optional callback object
   * @param {Function} [callbackFunction] Optional callback function in the callback object.
   * Signature is: function(callbackParams, error) {}.
   * If there is no error, error will be set to null.
   * @param {Hash} [callbackParams] Optional Hash to pass into the callback function.
   */
  synchronizeWithServer: function(clearLocalData, callbackTarget, callbackFunction, callbackParams) {
    // If operation already under way, just exit
    var isSynchronizingWithServer = this.get('isSynchronizingWithServer');
    if (isSynchronizingWithServer) {
      return;
    }

    if (clearLocalData) {
      var records = Chililog.store.find(Chililog.RepositoryInfoRecord);
      records.forEach(function(record) {
        record.destroy()
      });
      Chililog.store.commitRecords();
    }

    // Not logged in, so cannot sync
    var authToken = Chililog.sessionDataController.get('authenticationToken');
    if (SC.empty(authToken)) {
      return;
    }

    // We are working
    this.set('isSynchronizingWithServer', YES);

    // Get data
    var params = { callbackTarget: callbackTarget, callbackFunction: callbackFunction, callbackParams: callbackParams };
    var url = '/api/repository_info';
    var request = SC.Request.getUrl(url).async(YES).json(YES).header(Chililog.AUTHENTICATION_HEADER_NAME, authToken);
    request.notify(this, 'endSynchronizeWithServer', params).send();
  },

  /**
   * Process data when user information returns
   *
   * @param {SC.Response} response
   */
  endSynchronizeWithServer: function(response, params) {
    var error = null;
    try {
      // Check status
      this.checkResponse(response);

      // Set data
      var repoInfoAOArray = response.get('body');
      if (!SC.none(repoInfoAOArray) && SC.isArray(repoInfoAOArray)) {
        for (var i = 0; i < repoInfoAOArray.length; i++) {
          var repoInfoAO = repoInfoAOArray[i];

          // See if record exists
          var repoInfoRecord = Chililog.store.find(Chililog.RepositoryInfoRecord, repoInfoAO.DocumentID);
          if (SC.none(repoInfoRecord) || (repoInfoRecord.get('status') & SC.Record.DESTROYED)) {
            repoInfoRecord = Chililog.store.createRecord(Chililog.RepositoryInfoRecord, {}, repoInfoAO.DocumentID);
          }

          // Find corresponding repository record
          var query = SC.Query.local(Chililog.RepositoryRecord, 'name={name}', {name: repoInfoAO.Name});
          var repoRecords = Chililog.store.find(query);
          if (repoRecords.get('length') > 0) {
            repoInfoRecord.updateStatus(repoRecords.objectAt(0));
          }
          repoInfoRecord.fromApiObject(repoInfoAO);
        }
        Chililog.store.commitRecords();
      }

      // Delete records that have not been returned
      var records = Chililog.store.find(Chililog.RepositoryInfoRecord);
      records.forEach(function(record) {
        var doDelete = YES;
        if (!SC.none(repoInfoAOArray) && SC.isArray(repoInfoAOArray)) {
          for (var i = 0; i < repoInfoAOArray.length; i++) {
            var repoInfoAO = repoInfoAOArray[i];
            if (repoInfoAO[Chililog.DOCUMENT_ID_AO_FIELD_NAME] === record.get(Chililog.DOCUMENT_ID_RECORD_FIELD_NAME)) {
              doDelete = NO;
              break;
            }
          }
        }
        if (doDelete) {
          record.destroy()
        }
      });
      Chililog.store.commitRecords();
            
    }
    catch (err) {
      error = err;
      SC.Logger.error('repositoryInfoDataController.endSynchronizeWithServer: ' + err.message);
    }

    // Finish sync'ing
    this.set('isSynchronizingWithServer', NO);

    // Callback
    if (!SC.none(params.callbackFunction)) {
      params.callbackFunction.call(params.callbackTarget, params.callbackParams, error);
    }

    // Return YES to signal handling of callback
    return YES;
  },

  /**
   * Returns a new user record for editing
   *
   * @returns {Chililog.UserRecord}
   */
  create: function(documentID) {
    var nestedStore = Chililog.store.chain();
    var record = nestedStore.createRecord(Chililog.RepositoryInfoRecord, {});
    record.set(Chililog.DOCUMENT_VERSION_RECORD_FIELD_NAME,  0);
    record.set('maxKeywords', 20);
    record.set('writeQueueWorkerCount',  1);
    record.set('writeQueueMaxMemory',  20971520); //20MB
    record.set('writeQueuePageSize',  4194304);  //4MB
    record.set('writeQueuePageCountCache', 3);
    return record;
  },

  /**
   * Returns an existing the user record for editing
   *
   * @param {String} documentID Document ID of the user record to edit
   * @returns {Chililog.UserRecord}
   */
  edit: function(documentID) {
    var nestedStore = Chililog.store.chain();
    var record = nestedStore.find(Chililog.RepositoryInfoRecord, documentID);
    return record;
  },

  /**
   * Saves the user record to the server
   * @param {Chililog.RepositoryInfoRecord} record record to save
   * @param {Object} [callbackTarget] Optional callback object
   * @param {Function} [callbackFunction] Optional callback function in the callback object.
   * Signature is: function(documentID, callbackParams, error) {}.
   * documentID will be set to the id of the document that was saved
   * If there is no error, error will be set to null.
   * @param {Hash} [callbackParams] Optional Hash to pass into the callback function.
   */
  save: function(record, callbackTarget, callbackFunction, callbackParams) {
    var data = record.toApiObject();
    var authToken = Chililog.sessionDataController.get('authenticationToken');

    var documentID = record.get(Chililog.DOCUMENT_ID_RECORD_FIELD_NAME);
    var documentVersion = record.get(Chililog.DOCUMENT_VERSION_RECORD_FIELD_NAME);
    var isAdding = (SC.none(documentVersion) || documentVersion ===  0);
    var request;

    if (isAdding) {
      var url = '/api/repository_info/';
      request = SC.Request.postUrl(url).async(YES).json(YES).header(Chililog.AUTHENTICATION_HEADER_NAME, authToken);
    } else {
      var url = '/api/repository_info/' + documentID;
      request = SC.Request.putUrl(url).async(YES).json(YES).header(Chililog.AUTHENTICATION_HEADER_NAME, authToken);
    }
    var params = { isAdding: isAdding, documentID: documentID,
      callbackTarget: callbackTarget, callbackFunction: callbackFunction, callbackParams: callbackParams
    }
      ;
    request.notify(this, 'endSave', params).send(data);

    return;
  },

  /**
   * Callback from save() after we get a response from the server to process
   * the returned info.
   *
   * @param {SC.Response} response The HTTP response
   * @param {Hash} params Hash of parameters passed into SC.Request.notify()
   * @returns {Boolean} YES if successful
   */
  endSave: function(response, params) {
    var error = null;
    try {
      // Check status
      this.checkResponse(response);

      // Save new authenticated user details
      var apiObject = response.get('body');
      if (params.documentID !== apiObject[Chililog.DOCUMENT_ID_AO_FIELD_NAME]) {
        throw Chililog.$error('_documentIDError', [ params.documentID, apiObject[Chililog.DOCUMENT_ID_AO_FIELD_NAME]]);
      }      

      var record = null;
      if (params.isAdding) {
        record = Chililog.store.createRecord(Chililog.RepositoryInfoRecord, {}, params.documentID);
      } else {
        record = Chililog.store.find(Chililog.RepositoryInfoRecord, params.documentID);
      }
      record.fromApiObject(apiObject);
      Chililog.store.commitRecords();
    }
    catch (err) {
      error = err;
      SC.Logger.error('repositoryInfoDataController.endSaveRecord: ' + err);
    }

    // Callback
    if (!SC.none(params.callbackFunction)) {
      params.callbackFunction.call(params.callbackTarget, params.documentID, params.callbackParams, error);
    }

    // Return YES to signal handling of callback
    return YES;
  },

  /**
   * Discard changes
   * @param {Chililog.RepositoryInfoRecord} record record to discard
   */
  discardChanges: function(record) {
    if (!SC.none(record)) {
      var nestedStore = record.get('store');
      nestedStore.destroy();
    }
    return;
  },

  /**
   * Deletes the repository info record on the server
   *
   * @param {String} documentID id of record to delete
   * @param {Object} [callbackTarget] Optional callback object
   * @param {Function} [callbackFunction] Optional callback function in the callback object.
   * Signature is: function(documentID, callbackParams, error) {}.
   * documentID will be set to the id of the document that was saved
   * If there is no error, error will be set to null.
   * @param {Hash} [callbackParams] Optional Hash to pass into the callback function.
   */
  erase: function(documentID, callbackTarget, callbackFunction, callbackParams) {
    var authToken = Chililog.sessionDataController.get('authenticationToken');

    var url = '/api/repository_info/' + documentID;
    var request = SC.Request.deleteUrl(url).async(YES).json(YES).header(Chililog.AUTHENTICATION_HEADER_NAME, authToken);
    var params = { documentID: documentID, callbackTarget: callbackTarget, callbackFunction: callbackFunction, callbackParams: callbackParams };
    request.notify(this, 'endErase', params).send({dummy: 'data'});

    return;
  },

  /**
   * Callback from save() after we get a response from the server to process
   * the returned info.
   *
   * @param {SC.Response} response The HTTP response
   * @param {Hash} params Hash of parameters passed into SC.Request.notify()
   * @returns {Boolean} YES if successful
   */
  endErase: function(response, params) {
    var error = null;
    try {
      // Check status
      this.checkResponse(response);

      var record = Chililog.store.find(Chililog.RepositoryInfoRecord, params.documentID);
      record.destroy();

      Chililog.store.commitRecords();
    }
    catch (err) {
      error = err;
      SC.Logger.error('repositoryInfoDataController.endErase: ' + err);
    }

    // Callback
    if (!SC.none(params.callbackFunction)) {
      params.callbackFunction.call(params.callbackTarget, params.documentID, params.callbackParams, error);
    }

    // Return YES to signal handling of callback
    return YES;
  }



});
