// ==========================================================================
// Project:   Chililog
// Copyright: ©2011 My Company, Inc.
// ==========================================================================

/**
 * State chart for configure screens
 */
Chililog.ConfigureState = SC.State.extend({

  initialSubstate: 'viewingRepositoryInfo',

  /**
   * Show my profile page in the body
   */
  enterState: function() {
    Chililog.configureTreeViewController.populate();
    Chililog.mainViewController.doShow('configure');
  },

  /**
   * List repositories in table view
   */
  viewingRepositoryInfo: SC.State.design({
    enterState: function() {
      Chililog.configureView.setPath('body.bottomRightView.contentView', null);
    },

    exitState: function() {
    }
  }),

  /**
   * Blank repository details view for user to add
   */
  creatingRepositoryInfo: SC.State.design({
    enterState: function() {
    },

    exitState: function() {
    }
  }),

  /**
   * Load existing repository details view for user to edit
   */
  editingRepositoryInfo: SC.State.design({
    /**
     * Load repository record via the data controller and put it in the view controller
     *
     * @param {Hash} context Data hash with 'documentID' set to the document id f the repository to edit
     */
    enterState: function(context) {
      var record = Chililog.repositoryInfoDataController.edit(context.documentID);
      Chililog.configureRepositoryInfoViewController.set('content', record);
      Chililog.configureRepositoryInfoViewController.show();
    },

    /**
     * Discard changes unless we are saving
     *
     * @param {Hash} context Data hash with 'isSaving' flag to indicate if we are moving to the save
     */
    exitState: function(context) {
      var isSaving = !SC.none(context) && context['isSaving'];
      if (!isSaving) {
        var record = Chililog.configureRepositoryInfoViewController.get('content');
        Chililog.repositoryInfoDataController.discardChanges(record);
      }
    }
  }),

  /**
   * Asynchronous call triggered to save repository details and wait for server to response
   */
  savingRepositoryInfo: SC.State.design({
    enterState: function() {
    },

    exitState: function() {
    }
  }),

  /**
   * List users in table view
   */
  viewingUsers: SC.State.design({
    enterState: function() {
      Chililog.configureView.setPath('body.bottomRightView.contentView', null);
    },

    exitState: function() {
    }
  }),

  /**
   * Show blank user details page for adding a new user
   */
  creatingUser: SC.State.design({
    /**
     * Load user record via the data controller and put it in the view controller
     *
     * @param {Hash} context Data hash with 'documentID' set to the document id f the user to edit. Alternatively,
     *  set 'reedit' to YES, then data will be left as is.
     *
     */
    enterState: function(context) {
      var isReedit = !SC.none(context) && context['isReedit'];
      if (!isReedit) {
        var record = Chililog.userDataController.create();
        Chililog.configureUserViewController.set('content', record);
        Chililog.configureUserViewController.set('isSaving', NO);
        Chililog.configureUserViewController.show();
        Chililog.configureTreeViewController.clearSelection();
      }
    },

    /**
     * Discard changes unless we are saving or re-editing
     *
     * @param {Hash} context Data hash with 'isSaving' flag to indicate if we are moving to the save
     */
    exitState: function(context) {
      var isSaving = !SC.none(context) && context['isSaving'];
      var isReedit = !SC.none(context) && context['isReedit'];
      if (!isSaving && !isReedit) {
        var record = Chililog.configureUserViewController.get('content');
        Chililog.userDataController.discardChanges(record);
      }
    },

    /**
     * Save changes
     */
    save: function() {
      this.gotoState('savingUser', {isSaving: YES});
    },

    /**
     * Discard changes and reload our data to the
     */
    discardChanges: function() {
      var record = Chililog.configureUserViewController.get('content');
      this.gotoState('creatingUser');
    }
  }),

  /**
   * Load existing user for the user to edit
   */
  editingUser: SC.State.design({
    /**
     * Load user record via the data controller and put it in the view controller
     *
     * @param {Hash} context Data hash with 'documentID' set to the document id f the user to edit. Alternatively,
     *  set 'reedit' to YES, then data will be left as is.
     *
     */
    enterState: function(context) {
      var isReedit = !SC.none(context) && context['isReedit'];
      if (!isReedit) {
        var record = Chililog.userDataController.edit(context['documentID']);
        Chililog.configureUserViewController.set('content', record);
        Chililog.configureUserViewController.set('isSaving', NO);
        Chililog.configureUserViewController.show();
      }
    },

    /**
     * Discard changes unless we are saving or re-editing
     *
     * @param {Hash} context Data hash with 'isSaving' flag to indicate if we are moving to the save
     */
    exitState: function(context) {
      var isSaving = !SC.none(context) && context['isSaving'];
      var isReedit = !SC.none(context) && context['isReedit'];
      if (!isSaving && !isReedit) {
        var record = Chililog.configureUserViewController.get('content');
        Chililog.userDataController.discardChanges(record);
      }
    },

    /**
     * Save changes
     */
    save: function() {
      this.gotoState('savingUser', {isSaving: YES});
    },

    /**
     * Discard changes and reload our data to the
     */
    discardChanges: function() {
      var record = Chililog.configureUserViewController.get('content');
      this.gotoState('editingUser', {documentID: record.get(Chililog.DOCUMENT_ID_RECORD_FIELD_NAME)});
    }
  }),

  /**
   * Asynchronous call triggered to save user details and wait for server to response
   */
  savingUser: SC.State.design({
    enterState: function() {
      Chililog.configureUserViewController.set('isSaving', YES);
      this.save();
    },

    exitState: function() {
      Chililog.configureUserViewController.set('isSaving', NO);
    },

    /**
     * Saves the user's details
     */
    save: function() {
      var ctrl = Chililog.configureUserViewController;
      try {
        Chililog.userDataController.save(ctrl.get('content'), this, this.endSave);
      }
      catch (error) {
        SC.Logger.error('savingUser.save: ' + error);
        ctrl.showSaveError(error);
        var stateToGoTo = ctrl.get('isCreating') ? 'creatingUser' : 'editingUser';
        this.gotoState(stateToGoTo, {isReedit: YES});
      }
    },

    /**
     * Callback from save() after we get a response from the server to process the returned info.
     *
     * @param {String} document id of the saved record. Null if error.
     * @param {SC.Error} error Error object or null if no error.
     */
    endSave: function(documentID, error) {
      var ctrl = Chililog.configureUserViewController;
      if (SC.none(error)) {
        // Show saved record
        ctrl.showSaveSuccess();
        this.gotoState('editingUser', {documentID: documentID});
      } else {
        // Show error
        ctrl.showSaveError(error);
        var stateToGoTo = ctrl.get('isCreating') ? 'creatingUser' : 'editingUser';
        this.gotoState(stateToGoTo, {isReedit: YES});
      }
    }
  }),

  /**
   * Asynchronous call triggered to delete the user
   */
  erasingUser: SC.State.design({
    enterState: function(context) {
      Chililog.configureUserViewController.set('isErasing', YES);
      this.erase(context);
    },

    exitState: function() {
      Chililog.configureUserViewController.set('isErasing', NO);
    },

    /**
     * Delete the user
     */
    erase: function(context) {
      var ctrl = Chililog.configureUserViewController;
      try {
        Chililog.userDataController.erase(context['documentID'], this, this.endErase);
      }
      catch (error) {
        SC.Logger.error('erasingUser.erase: ' + error);
        ctrl.showSaveError(error);
        this.gotoState('editingUser', {documentID: context['documentID']});
      }
    },

    /**
     * Callback from save() after we get a response from the server to process the returned info.
     *
     * @param {String} document id of the saved record. Null if error.
     * @param {SC.Error} error Error object or null if no error.
     */
    endErase: function(documentID, error) {
      var ctrl = Chililog.configureUserViewController;
      if (SC.none(error)) {
        Chililog.configureTreeViewController.selectUsersNode();
      } else {
        // Show error
        ctrl.showSaveError(error);
        this.gotoState('editingUser', {documentID: documentID});
      }
    }
  }),

  /**
   * Event to view repositories
   */
  viewRepositoryInfo: function() {
    this.gotoState('viewingRepositoryInfo');
  },

  /**
   * Event to view users
   */
  viewUsers: function() {
    this.gotoState('viewingUsers');
  },

  /**
   * Event to start adding a new user
   */
  createUser: function() {
    this.gotoState('creatingUser');
  },

  /**
   * Event to start editing users
   * 
   * @param {String} documentID unique id for the user record
   */
  editUser: function(documentID) {
    this.gotoState('editingUser', {documentID: documentID});
  },

  /**
   * Event to start deleting user
   *
   * @param {String} documentID unique id for the user record
   */
  eraseUser: function(documentID) {
    this.gotoState('erasingUser', {documentID: documentID});
  },

  /**
   * Event to start editing repository info
   *
   * @param {String} documentID unique id for the user record
   */
  editRepositoryInfo: function(documentID) {
    this.gotoState('editingRepositoryInfo', {documentID: documentID});
  }
  
});