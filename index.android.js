"use strict";

import { NativeModules } from "react-native";
import { mapParameters } from "./utils";

const Braintree = NativeModules.Braintree;

module.exports = {
  setup(token) {
    return new Promise(function (resolve, reject) {
      Braintree.setup(
        token,
        (test) => resolve(test),
        (err) => reject(err)
      );
    });
  },

  getCardNonce(parameters = {}) {
    return new Promise(function (resolve, reject) {
      Braintree.getCardNonce(
        mapParameters(parameters),
        (nonce) => resolve(nonce),
        (err) => reject(err)
      );
    });
  },

  showPaymentViewController(config = {}) {
    var options = {
      callToActionText: config.callToActionText,
      title: config.title,
      description: config.description,
      amount: config.amount,
      threeDSecure: config.threeDSecure,
    };
    return new Promise(function (resolve, reject) {
      Braintree.paymentRequest(
        options,
        (nonce) => resolve(nonce),
        (error) => reject(error)
      );
    });
  },

  showPayPalViewController() {
    return new Promise(function (resolve, reject) {
      Braintree.paypalRequest(
        (nonce) => resolve(nonce),
        (error) => reject(error)
      );
    });
  },

  constants: {
    ENVIRONMENT_TEST: Braintree.ENVIRONMENT_TEST,
    ENVIRONMENT_PRODUCTION: Braintree.ENVIRONMENT_PRODUCTION,
  },

  checkGPayIsEnable(environment, cardNetworks) {
    return Braintree.checkGPayIsEnable(environment, cardNetworks);
  },

  showGooglePayViewController(environment, requestData) {
    return new Promise(function (resolve, reject) {
      Braintree.showGooglePayViewController(
        environment, 
        requestData,
        (data) => resolve(data),
        (err) => reject(err)
      );
    });
    // return Braintree.showGooglePayViewController(environment, requestData);
  },
};
