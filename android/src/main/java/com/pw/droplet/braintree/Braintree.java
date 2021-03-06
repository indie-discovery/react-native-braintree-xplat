package com.pw.droplet.braintree;

import java.util.Map;
import java.util.HashMap;

import android.util.Log;

import com.braintreepayments.api.GooglePayment;
import com.braintreepayments.api.interfaces.BraintreeCancelListener;
import com.braintreepayments.api.models.GooglePaymentRequest;
import com.google.android.gms.wallet.TransactionInfo;
import com.google.gson.Gson;

import android.content.Intent;
import android.content.Context;
import android.app.Activity;
import android.widget.Toast;

import com.braintreepayments.api.ThreeDSecure;
import com.braintreepayments.api.PaymentRequest;
import com.braintreepayments.api.models.PaymentMethodNonce;
import com.braintreepayments.api.BraintreePaymentActivity;
import com.braintreepayments.api.BraintreeFragment;
import com.braintreepayments.api.exceptions.InvalidArgumentException;
import com.braintreepayments.api.exceptions.BraintreeError;
import com.braintreepayments.api.exceptions.ErrorWithResponse;
import com.braintreepayments.api.models.CardBuilder;
import com.braintreepayments.api.Card;
import com.braintreepayments.api.PayPal;
import com.braintreepayments.api.interfaces.PaymentMethodNonceCreatedListener;
import com.braintreepayments.api.interfaces.BraintreeErrorListener;
import com.braintreepayments.api.models.CardNonce;

import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.ReadableArray;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wallet.AutoResolveHelper;
import com.google.android.gms.wallet.IsReadyToPayRequest;
import com.google.android.gms.wallet.PaymentData;
import com.google.android.gms.wallet.PaymentDataRequest;
import com.google.android.gms.wallet.PaymentsClient;
import com.google.android.gms.wallet.Wallet;
import com.google.android.gms.wallet.WalletConstants;

public class Braintree extends ReactContextBaseJavaModule implements ActivityEventListener {
    private static final int PAYMENT_REQUEST = 65535;
    private String token;

    private Callback successCallback;
    private Callback errorCallback;

    private Context mActivityContext;
    private ReactApplicationContext rContext;
    private GPay gPay;

    private BraintreeFragment mBraintreeFragment;

    private ReadableMap threeDSecureOptions;

    private static final String ENVIRONMENT_PRODUCTION_KEY = "ENVIRONMENT_PRODUCTION";

    private static final String ENVIRONMENT_TEST_KEY = "ENVIRONMENT_TEST";

    public Braintree(ReactApplicationContext reactContext) {
        super(reactContext);
        this.rContext = reactContext;
        this.gPay = new GPay(reactContext);
        reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "Braintree";
    }

    public String getToken() {
        return this.token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @ReactMethod
    public void setup(final String token, final Callback successCallback, final Callback errorCallback) {
        try {
            this.mBraintreeFragment = BraintreeFragment.newInstance(getCurrentActivity(), token);
            this.mBraintreeFragment.addListener(new BraintreeCancelListener() {
                @Override
                public void onCancel(int requestCode) {
                    nonceErrorCallback("USER_CANCELLATION");
                }
            });
            this.mBraintreeFragment.addListener(new PaymentMethodNonceCreatedListener() {
                @Override
                public void onPaymentMethodNonceCreated(PaymentMethodNonce paymentMethodNonce) {
                    if (threeDSecureOptions != null && paymentMethodNonce instanceof CardNonce) {
                        CardNonce cardNonce = (CardNonce) paymentMethodNonce;
                        if (!cardNonce.getThreeDSecureInfo().isLiabilityShiftPossible()) {
                            nonceErrorCallback("3DSECURE_NOT_ABLE_TO_SHIFT_LIABILITY");
                        } else if (!cardNonce.getThreeDSecureInfo().isLiabilityShifted()) {
                            nonceErrorCallback("3DSECURE_LIABILITY_NOT_SHIFTED");
                        } else {
                            nonceCallback(paymentMethodNonce.getNonce());
                        }
                    } else {
                        nonceCallback(paymentMethodNonce.getNonce());
                    }
                }
            });
            this.mBraintreeFragment.addListener(new BraintreeErrorListener() {
                @Override
                public void onError(Exception error) {
                    Gson gson = new Gson();

                    if (error instanceof ErrorWithResponse) {
                        ErrorWithResponse errorWithResponse = (ErrorWithResponse) error;
                        BraintreeError cardErrors = errorWithResponse.errorFor("creditCard");
                        if (cardErrors != null) {
                            Log.d("cardErrors != null:", gson.toJson(cardErrors));

                            // Gson gson = new Gson();
                            final Map<String, String> errors = new HashMap<>();
                            BraintreeError numberError = cardErrors.errorFor("number");
                            BraintreeError cvvError = cardErrors.errorFor("cvv");
                            BraintreeError expirationDateError = cardErrors.errorFor("expirationDate");
                            BraintreeError postalCode = cardErrors.errorFor("postalCode");
                            BraintreeError base = cardErrors.errorFor("base");

                            if (numberError != null) {
                                errors.put("card_number", numberError.getMessage());
                            }

                            if (cvvError != null) {
                                errors.put("cvv", cvvError.getMessage());
                            }

                            if (expirationDateError != null) {
                                errors.put("expiration_date", expirationDateError.getMessage());
                            }

                            if (base != null) {
                                errors.put("base", base.getMessage());
                            }

                            // TODO add more fields
                            if (postalCode != null) {
                                errors.put("postal_code", postalCode.getMessage());
                            }

                            nonceErrorCallback(gson.toJson(errors));
                        } else {
                            Log.d("errorWithResponse", gson.toJson(errorWithResponse));

                            nonceErrorCallback(errorWithResponse.getErrorResponse());
                        }
                    }
                }
            });
            this.setToken(token);
            successCallback.invoke(this.getToken());
        } catch (InvalidArgumentException e) {
            errorCallback.invoke(e.getMessage());
        }
    }

    @ReactMethod
    public void getCardNonce(final ReadableMap parameters, final Callback successCallback,
            final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;

        CardBuilder cardBuilder = new CardBuilder().validate(true);

        if (parameters.hasKey("number"))
            cardBuilder.cardNumber(parameters.getString("number"));

        if (parameters.hasKey("cvv"))
            cardBuilder.cvv(parameters.getString("cvv"));

        // In order to keep compatibility with iOS implementation, do not accept
        // expirationMonth and exporationYear,
        // accept rather expirationDate (which is combination of
        // expirationMonth/expirationYear)
        if (parameters.hasKey("expirationDate"))
            cardBuilder.expirationDate(parameters.getString("expirationDate"));

        if (parameters.hasKey("cardholderName"))
            cardBuilder.cardholderName(parameters.getString("cardholderName"));

        if (parameters.hasKey("firstName"))
            cardBuilder.firstName(parameters.getString("firstName"));

        if (parameters.hasKey("lastName"))
            cardBuilder.lastName(parameters.getString("lastName"));

        if (parameters.hasKey("company"))
            cardBuilder.company(parameters.getString("company"));

        if (parameters.hasKey("countryName"))
            cardBuilder.countryName(parameters.getString("countryName"));

        if (parameters.hasKey("countryCodeAlpha2"))
            cardBuilder.countryCodeAlpha2(parameters.getString("countryCodeAlpha2"));

        if (parameters.hasKey("countryCodeAlpha3"))
            cardBuilder.countryCodeAlpha3(parameters.getString("countryCodeAlpha3"));

        if (parameters.hasKey("countryCodeNumeric"))
            cardBuilder.countryCodeNumeric(parameters.getString("countryCodeNumeric"));

        if (parameters.hasKey("locality"))
            cardBuilder.locality(parameters.getString("locality"));

        if (parameters.hasKey("postalCode"))
            cardBuilder.postalCode(parameters.getString("postalCode"));

        if (parameters.hasKey("region"))
            cardBuilder.region(parameters.getString("region"));

        if (parameters.hasKey("streetAddress"))
            cardBuilder.streetAddress(parameters.getString("streetAddress"));

        if (parameters.hasKey("extendedAddress"))
            cardBuilder.extendedAddress(parameters.getString("extendedAddress"));

        Card.tokenize(this.mBraintreeFragment, cardBuilder);
    }

    public void nonceCallback(String nonce) {
        this.successCallback.invoke(nonce);
    }

    public void nonceErrorCallback(String error) {
        this.errorCallback.invoke(error);
    }

    @ReactMethod
    public void paymentRequest(final ReadableMap options, final Callback successCallback,
            final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;
        PaymentRequest paymentRequest = null;

        String callToActionText = null;
        String title = null;
        String description = null;
        String amount = null;

        if (options.hasKey("callToActionText")) {
            callToActionText = options.getString("callToActionText");
        }

        if (options.hasKey("title")) {
            title = options.getString("title");
        }

        if (options.hasKey("description")) {
            description = options.getString("description");
        }

        if (options.hasKey("amount")) {
            amount = options.getString("amount");
        }

        if (options.hasKey("threeDSecure")) {
            this.threeDSecureOptions = options.getMap("threeDSecure");
        }

        paymentRequest = new PaymentRequest().submitButtonText(callToActionText).primaryDescription(title)
                .secondaryDescription(description).amount(amount).clientToken(this.getToken());

        (getCurrentActivity()).startActivityForResult(paymentRequest.getIntent(getCurrentActivity()), PAYMENT_REQUEST);
    }

    @ReactMethod
    public void paypalRequest(final Callback successCallback, final Callback errorCallback) {
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;
        PayPal.authorizeAccount(this.mBraintreeFragment);
    }

    @Override
    public void onActivityResult(Activity activity, final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == PAYMENT_REQUEST) {
            switch (resultCode) {
            case Activity.RESULT_OK:
                PaymentMethodNonce paymentMethodNonce = data
                        .getParcelableExtra(BraintreePaymentActivity.EXTRA_PAYMENT_METHOD_NONCE);

                if (this.threeDSecureOptions != null) {
                    ThreeDSecure.performVerification(this.mBraintreeFragment, paymentMethodNonce.getNonce(),
                            String.valueOf(this.threeDSecureOptions.getDouble("amount")));
                } else {
                    this.successCallback.invoke(paymentMethodNonce.getNonce());
                }
                break;
            case BraintreePaymentActivity.BRAINTREE_RESULT_DEVELOPER_ERROR:
            case BraintreePaymentActivity.BRAINTREE_RESULT_SERVER_ERROR:
            case BraintreePaymentActivity.BRAINTREE_RESULT_SERVER_UNAVAILABLE:
                this.errorCallback.invoke(data.getSerializableExtra(BraintreePaymentActivity.EXTRA_ERROR_MESSAGE));
                break;
            case Activity.RESULT_CANCELED:
                this.errorCallback.invoke("USER_CANCELLATION");
                break;
            default:
                break;
            }
        }
    }

    public void onNewIntent(Intent intent) {
    }

    /**
     * Google Pay Implementation
     */
    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put(ENVIRONMENT_PRODUCTION_KEY, WalletConstants.ENVIRONMENT_PRODUCTION);
        constants.put(ENVIRONMENT_TEST_KEY, WalletConstants.ENVIRONMENT_TEST);
        return constants;
    }

    @ReactMethod
    public void checkGPayIsEnable(int environment, ReadableArray cardNetworks, final Promise promise) {
        this.gPay.checkGPayIsEnable(environment, cardNetworks, promise, getCurrentActivity());
        return;
    }

    @ReactMethod
    public void showGooglePayViewController(String environment, ReadableMap requestData, final Callback successCallback, final Callback errorCallback) {
        // environment = PRODUCTION / TEST
        this.successCallback = successCallback;
        this.errorCallback = errorCallback;

        ReadableMap transaction = requestData.getMap("transaction");
        String merchantId = requestData.getString("merchantId");
        Log.e("totalPrice", transaction.getString("totalPrice"));
        Log.e("currencyCode", transaction.getString("currencyCode"));
        
        GooglePaymentRequest googlePaymentRequest = new GooglePaymentRequest()
                .transactionInfo(TransactionInfo.newBuilder()
                        .setTotalPrice(transaction.getString("totalPrice"))
                        .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                        .setCurrencyCode(transaction.getString("currencyCode"))
                        .build())
                .environment(environment)
                .googleMerchantId(merchantId);
        GooglePayment.requestPayment(this.mBraintreeFragment, googlePaymentRequest);
        // this.gPay.show(environment, requestData, promise, getCurrentActivity());
        return;
    }
}
