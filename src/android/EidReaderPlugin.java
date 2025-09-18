package cordova.plugin.eidreader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.widget.Toast;

import org.apache.cordova.*;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EidReaderPlugin extends CordovaPlugin {

    private static final String TAG = "EidReaderPlugin";
    private static final String ACTION_DATAWEDGE_SCAN = "cordova.plugin.eidreader.ACTION";
    private static final String EMIRATES_ID_MRZ_PATTERN = "([A-Z0-9<]{30})([A-Z0-9<]{30})([A-Z0-9<]{30})";

    private static CallbackContext callbackContext;

    private static void debug(Context context, String message) {
        Log.d(TAG, message);
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } else {
            Log.d(TAG, "⚠️ Context is null, cannot show Toast: " + message);
        }
    }

    public static class DataWedgeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            debug(context, "📡 Receiver triggered");

            String scannedData = intent.getStringExtra("com.symbol.datawedge.data_string");
            if (scannedData == null || scannedData.trim().isEmpty()) {
                debug(context, "⚠️ No scanned data received");
                return;
            }

            debug(context, "Scanned Data: " + scannedData);
            String formattedData = scannedData.replace("\n", "").replace("\r", "");
            JSONObject result = new JSONObject();

            try {
                if (isValidEmiratesIdMRZ(formattedData)) {
                    debug(context, "✅ Valid Emirates ID scanned");

                    String emiratesIdNumber = extractEmiratesId(formattedData);
                    String documentNumber = extractDocumentNumber(formattedData);
                    String issuingState = formattedData.substring(2, 5);
                    String dateOfBirth = parseDateFromMRZ(formattedData.substring(30, 36));
                    String sex = formattedData.substring(37, 38);
                    String expiryDate = parseDateFromMRZ(formattedData.substring(38, 44));
                    String nationality = formattedData.substring(45, 48);
                    String nameMRZ = formattedData.substring(60).replace("<", " ").trim();

                    result.put("status", "success");
                    result.put("idNumber", emiratesIdNumber);
                    result.put("documentNumber", documentNumber);
                    result.put("name", nameMRZ);
                    result.put("issuingState", issuingState);
                    result.put("dateOfBirth", dateOfBirth);
                    result.put("sex", sex);
                    result.put("expiryDate", expiryDate);
                    result.put("nationality", nationality);

                    if (callbackContext != null) {
                        callbackContext.success(result);
                    }
                } else {
                    debug(context, "❌ Invalid Emirates ID format");
                    result.put("status", "error");
                    result.put("message", "Scanned item is not a valid Emirates ID.");
                    if (callbackContext != null) {
                        callbackContext.success(result);
                    }
                }
            } catch (Exception e) {
                debug(context, "⚠️ Parsing error occurred: " + e.getMessage());
                if (callbackContext != null) {
                    callbackContext.error("Parsing error");
                }
            }
        }

        private boolean isValidEmiratesIdMRZ(String data) {
            return data != null && data.length() == 90 && Pattern.matches(EMIRATES_ID_MRZ_PATTERN, data);
        }

        private String extractEmiratesId(String data) {
            Matcher matcher = Pattern.compile("784\\d{12}").matcher(data);
            return matcher.find() ? matcher.group() : "Not found";
        }

        private String extractDocumentNumber(String data) {
            Matcher matcher = Pattern.compile("ILARE([A-Z0-9]{9})").matcher(data);
            return matcher.find() ? matcher.group(1) : "Not found";
        }

        private String parseDateFromMRZ(String mrzDate) {
            if (mrzDate == null || mrzDate.length() != 6) {
                return "Invalid Date";
            }
            try {
                int currentYear = java.time.Year.now().getValue();
                int currentTwoDigitYear = currentYear % 100;
                int mrzTwoDigitYear = Integer.parseInt(mrzDate.substring(0, 2));

                String fullYear = (mrzTwoDigitYear > currentTwoDigitYear + 20)
                        ? "19" + String.format(Locale.US, "%02d", mrzTwoDigitYear)
                        : "20" + String.format(Locale.US, "%02d", mrzTwoDigitYear);

                String fullDateString = fullYear + mrzDate.substring(2);
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
                SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                return outputFormat.format(inputFormat.parse(fullDateString));
            } catch (ParseException e) {
                debug(null, "Date parsing error: " + e.getMessage());
                return "Parsing error";
            }
        }
    }

    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) {
        if ("startListening".equals(action)) {
            EidReaderPlugin.callbackContext = callbackContext;

            IntentFilter filter = new IntentFilter();
            filter.addCategory(Intent.CATEGORY_DEFAULT);
            filter.addAction(ACTION_DATAWEDGE_SCAN);
            cordova.getActivity().registerReceiver(new DataWedgeReceiver(), filter);
            debug(cordova.getActivity(), "✅ DataWedge receiver registered.");

            return true;
        }
        return false;
    }
}
