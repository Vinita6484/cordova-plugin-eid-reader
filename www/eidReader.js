var exec = require('cordova/exec');

exports.startListening = function (successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'EidReaderPlugin', 'startListening', []);
};
