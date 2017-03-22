
/*
 This contains the "business logic" for the topology
 overlay that we are implementing.
 */

(function () {
    'use strict';

    // injected refs
    var $log, fs, flash, wss;

    // constants
    var displayStart = 'LmTopoDisplayStart',
        displayUpdate = 'LmTopoDisplayUpdate',
        displayStop = 'LmTopoDisplayStop';

    // internal state
    var currentMode = null;


    // === ---------------------------
    // === Helper functions

    function sendDisplayStart(mode) {
        wss.sendEvent(displayStart, {
            mode: mode
        });
    }

    function sendDisplayUpdate(what) {
        wss.sendEvent(displayUpdate, {
            id: what ? what.id : ''
        });
    }

    function sendDisplayStop() {
        wss.sendEvent(displayStop);
    }

    
    // === ---------------------------
    // === Main API functions

    function startDisplay(mode) {
        if (currentMode === mode) {
            $log.debug('(in mode', mode, 'already)');
        } else {
            currentMode = mode;
            sendDisplayStart(mode);
            if (mode === 'device') {
            	flash.flash('Device mode: On');
            } else {
            	flash.flash('Link mode: On');
        	}
        }
    }

    function updateDisplay(m) {
        if (currentMode) {
            sendDisplayUpdate(m);
        }
    }

    function stopDisplay() {
        if (currentMode) {
            currentMode = null;
            sendDisplayStop();
        	flash.flash('Link quality monitor modes: Off');
            return true;
        }
        return false;
    }

  
    // === ---------------------------
    // === Module Factory Definition

    angular.module('ovLmTopoOv', [])
        .factory('LmTopoOvService',
        ['$log', 'FnService', 'FlashService', 'WebSocketService',

        function (_$log_, _fs_, _flash_, _wss_) {
            $log = _$log_;
            fs = _fs_;
            flash = _flash_;
            wss = _wss_;

            return {
                startDisplay: startDisplay,
                updateDisplay: updateDisplay,
                stopDisplay: stopDisplay
            };
        }]);
}());