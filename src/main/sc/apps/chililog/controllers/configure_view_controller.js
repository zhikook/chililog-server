// ==========================================================================
// Project:   Chililog.mainPaneController
// Copyright: ©2011 My Company, Inc.
// ==========================================================================

/**********************************************************************************************************************
 * Users
 **********************************************************************************************************************/

/**
 * Controls the data when configuring users
 */
Chililog.configureUserDetailViewController = SC.ObjectController.create({

  /**
   * User record to display
   * @type Chililog.UserRecord
   */
  content: null,

  /**
   * Flag to indicate if we are creating 
   */
  isCreating: function() {
    var record = this.get('content');
    if (!SC.none(record) && record.get(Chililog.DOCUMENT_VERSION_RECORD_FIELD_NAME) === 0) {
      return YES;
    }
    return NO;
  }.property('content').cacheable(),

  /**
   * Adjust height of body box depending on if we are adding or not
   */
  bodyLayout: function() {
    if (this.get('isCreating')) {
      return { top: 35, left: 0, width: 400, height: 450 };
    } else {
      return { top: 35, left: 0, width: 400, height: 330 };
    }
  }.property('isCreating').cacheable(),

  /**
   * Adjust height of buttons depending on if we are adding or not
   */
  buttonsLayout: function() {
    if (this.get('isCreating')) {
      return {top: 390, left: 20, right: 20, height: 50 };
    } else {
      return {top: 270, left: 20, right: 20, height: 50 };
    }
  }.property('isCreating').cacheable(),

  /**
   * Show the user details form
   */
  show: function() {
    Chililog.configureView.setPath('body.bottomRightView.contentView', Chililog.configureUserView);
    var field = Chililog.configureUserView.getPath('body.username.field');
    field.becomeFirstResponder();
  },

  /**
   * Flag to indicate if the user's profile can be saved.
   * Can only be saved if form is loaded and the data has changed
   *
   * @type Boolean
   */
  canSave: function() {
    var recordStatus = this.getPath('content.status');
    if (!SC.none(recordStatus) && recordStatus !== SC.Record.READY_CLEAN && !this.get('isSaving')) {
      return YES;
    }
    return NO;
  }.property('content.status', 'isSaving').cacheable(),

  /**
   * Flag to indicate if we are in the middle of trying to save a profile
   */
  isSaving: NO,

  /**
   * Trigger event to create a new user
   */
  create: function() {
    Chililog.statechart.sendEvent('createUser');
  },

  /**
   * Trigger event to save the user's profile
   */
  save: function() {
    Chililog.statechart.sendEvent('save');
  },

  /**
   * Confirm erase
   */
  confirmErase: function() {
    SC.AlertPane.warn({
      message: '_configureUserView.ConfirmDelete'.loc(this.getPath('content.username')),
      buttons: [
        {
          title: '_delete'.loc(),
          action: this.erase
        },
        {
          title: '_cancel'.loc()
        }
      ]
    });
  },

  /**
   * Trigger event to delete the user. This is called back from confirmErase
   */
  erase: function() {
    var record = Chililog.configureUserViewController.get('content');
    Chililog.statechart.sendEvent('eraseUser', record.get(Chililog.DOCUMENT_ID_RECORD_FIELD_NAME));
  },

  /**
   * Trigger event to discard changes to the user's profile
   */
  discardChanges: function() {
    Chililog.statechart.sendEvent('discardChanges');
  },

  /**
   * Show success message when profile successfully saved
   */
  showSaveSuccess: function() {
    var view = Chililog.configureUserView.getPath('body.successMessage');
    var field = Chililog.configureUserView.getPath('body.username.field');

    if (!SC.none(view)) {
      // Have to invokeLater because of webkit
      // http://groups.google.com/group/sproutcore/browse_thread/thread/482740f497d80462/cba903f9cc6aadf8?lnk=gst&q=animate#cba903f9cc6aadf8
      view.adjust("opacity", 1);
      this.invokeLater(function() {
        view.animate("opacity", 0, { duration: 2, timing:'ease-in' });
      }, 10);
    }

    field.becomeFirstResponder();
  },

  /**
   * Show error message when error happened why trying to save profile
   * @param {SC.Error} error
   */
  showSaveError: function(error) {
    if (SC.instanceOf(error, SC.Error)) {
      // Error
      var message = error.get('message');
      SC.AlertPane.error({ message: message });

      var label = error.get('label');
      if (SC.empty(label)) {
        label = 'username';
      }

      var fieldPath = 'body.%@.field'.fmt(label);
      if (label === 'password' || label === 'confirmPassword') {
        fieldPath = 'body.passwords.%@.field'.fmt(label);
      }
      var field = Chililog.configureUserView.getPath(fieldPath);
      if (!SC.none(field)) {
        field.becomeFirstResponder();
      }
    } else {
      // Assume error message string
      SC.AlertPane.error(error);
    }
  }  
  
});

/**********************************************************************************************************************
 * Repositories
 **********************************************************************************************************************/

/**
 * Controls the data when configuring repositories
 */
Chililog.configureRepositoryInfoDetailViewController = SC.ObjectController.create({

  /**
   * Repository record to display
   * @type Chililog.RepositoryRecord
   */
  content: null,

  /**
   * Show the repository details form
   */
  show: function() {
    Chililog.configureView.setPath('body.bottomRightView.contentView', Chililog.configureRepositoryInfoView);
  }
});

/**********************************************************************************************************************
 * Main
 **********************************************************************************************************************/

/**
 * Controls the data when configuring repositories
 */
Chililog.configureViewController = SC.Object.create({

  onSelect: function() {
    var selectionSet = Chililog.configureView.getPath('left.contentView.selection');
    if (SC.none(selectionSet) || selectionSet.get('length') === 0) {
      return null;
    }
    var selection = selectionSet.get('firstObject');
    var id = selection['id'];
    if (id === 'Users') {
      Chililog.statechart.sendEvent('viewUsers');
    } else if (id === 'Repositories') {
      Chililog.statechart.sendEvent('viewRepositoryInfo');
    }

    return;
  },

  /**
   * Show list of repositories in the right hand side details pane
   */
  showRepositoryInfoList: function() {
    Chililog.configureView.setPath('right.contentView', Chililog.configureRepositoryInfoSceneView);
    Chililog.configureRepositoryInfoSceneView.set('nowShowing', 'Chililog.configureRepositoryInfoListView');
    return;
  },

  /**
   * Show list of repositories in the right hand side details pane
   */
  showRepositoryInfoDetail: function() {
    var currentView = Chililog.configureView.getPath('right.contentView');
    if (currentView !== Chililog.configureRepositoryInfoSceneView) {
      Chililog.configureView.setPath('right.contentView', Chililog.configureRepositoryInfoSceneView);
    }
    Chililog.configureRepositoryInfoSceneView.set('nowShowing', 'Chililog.configureRepositoryInfoDetailView');
    return;
  },

  /**
   * Show list of users in the right hand side details pane
   */
  showUserList: function() {
    Chililog.configureView.setPath('right.contentView', Chililog.configureUserSceneView);
    Chililog.configureUserSceneView.set('nowShowing', 'Chililog.configureUserListView');
    return;
  },

  /**
   * Show list of users in the right hand side details pane
   */
  showUserDetail: function() {
    var currentView = Chililog.configureView.getPath('right.contentView');
    if (currentView !== Chililog.configureUserSceneView) {
      Chililog.configureView.setPath('right.contentView', Chililog.configureUserSceneView);
    }
    Chililog.configureUserSceneView.set('nowShowing', 'Chililog.configureUserDetailView');
    return;
  }

});

