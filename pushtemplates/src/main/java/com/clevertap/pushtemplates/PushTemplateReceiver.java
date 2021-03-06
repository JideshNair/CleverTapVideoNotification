package com.clevertap.pushtemplates;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import androidx.core.content.ContextCompat;

import android.text.Html;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.clevertap.android.sdk.CleverTapAPI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

import static android.content.Context.NOTIFICATION_SERVICE;

public class PushTemplateReceiver extends BroadcastReceiver {
    boolean clicked1 = true, clicked2 = true, clicked3 = true, clicked4 = true, clicked5 = true, img1 = false, img2 = false, img3 = false, buynow = true, bigimage = true, cta1 = true, cta2 = true, cta3 = true, cta4 = true, cta5 = true, close = true;
    private RemoteViews contentViewBig, contentViewSmall, contentViewRating;
    private String pt_id;
    private TemplateType templateType;
    private String pt_title;
    private String pt_msg;
    private String pt_msg_summary;
    private String pt_img_small;
    private String pt_rating_default_dl;
    private String pt_title_clr, pt_msg_clr;
    private ArrayList<String> imageList = new ArrayList<>();
    private ArrayList<String> deepLinkList = new ArrayList<>();
    private ArrayList<String> bigTextList = new ArrayList<>();
    private ArrayList<String> smallTextList = new ArrayList<>();
    private ArrayList<String> priceList = new ArrayList<>();
    private String channelId;
    private int smallIcon = 0;
    private boolean requiresChannelId;
    private NotificationManager notificationManager;
    private CleverTapAPI cleverTapAPI;



    @Override
    public void onReceive(Context context, Intent intent) {

        if (intent.getExtras() != null) {
            Bundle extras = intent.getExtras();
            pt_id = intent.getStringExtra(Constants.PT_ID);
            pt_msg = extras.getString(Constants.PT_MSG);
            pt_msg_summary = extras.getString(Constants.PT_MSG_SUMMARY);
            pt_msg_clr = extras.getString(Constants.PT_MSG_COLOR);
            pt_title = extras.getString(Constants.PT_TITLE);
            pt_title_clr = extras.getString(Constants.PT_TITLE_COLOR);
            pt_img_small = extras.getString(Constants.PT_SMALL_IMG);
            pt_rating_default_dl = extras.getString(Constants.PT_DEFAULT_DL);
            imageList = Utils.getImageListFromExtras(extras);
            deepLinkList = Utils.getDeepLinkListFromExtras(extras);
            bigTextList = Utils.getBigTextFromExtras(extras);
            smallTextList = Utils.getSmallTextFromExtras(extras);
            priceList = Utils.getPriceFromExtras(extras);

            notificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
            cleverTapAPI = CleverTapAPI.getDefaultInstance(context);
            channelId = extras.getString(Constants.WZRK_CHANNEL_ID, "");

            requiresChannelId = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String channelIdError = null;
                if (channelId.isEmpty()) {
                    channelIdError = "Unable to render notification, channelId is required but not provided in the notification payload: " + extras.toString();
                } else if (notificationManager != null && notificationManager.getNotificationChannel(channelId) == null) {
                    channelIdError = "Unable to render notification, channelId: " + channelId + " not registered by the app.";
                }
                if (channelIdError != null) {
                    PTLog.verbose(channelIdError);
                    return;
                }
            }

            if (pt_id != null) {
                templateType = TemplateType.fromString(pt_id);
            }
            if (templateType != null) {
                switch (templateType) {
                    case RATING:
                        handleRatingNotification(context, extras);
                        break;
                    case FIVE_ICONS:
                        handleFiveCTANotification(context, extras);
                        break;
                    case PRODUCT_DISPLAY:
                        handleProductDisplayNotification(context, extras);
                        break;
                    case INPUT_BOX:
                        handleInputBoxNotification(context, extras, intent);
                        break;
                    case INPUT_VIDEO:
                        renderVideoNotification(context,extras);

                }
            }
        }
    }

    private void handleInputBoxNotification(Context context, Bundle extras, Intent intent){

        //Fetch Remote Input
        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);

        if (remoteInput != null) {
            //Fetch Reply
            CharSequence reply = remoteInput.getCharSequence(
                    Constants.PT_INPUT_KEY);

            int notificationId = extras.getInt(Constants.PT_NOTIF_ID);

            if (reply != null) {

                PTLog.verbose("Processing Input from Input Template");



                //Update the notification to show that the reply was received.
                NotificationCompat.Builder repliedNotification;
                if (requiresChannelId) {
                    repliedNotification = new NotificationCompat.Builder(context, channelId);
                } else {
                    repliedNotification = new NotificationCompat.Builder(context);
                }

                Bundle metaData;
                try {
                    PackageManager pm = context.getPackageManager();
                    ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
                    metaData = ai.metaData;
                    String x = Utils._getManifestStringValueForKey(metaData, Constants.LABEL_NOTIFICATION_ICON);
                    if (x == null) throw new IllegalArgumentException();
                    smallIcon = context.getResources().getIdentifier(x, "drawable", context.getPackageName());
                    if (smallIcon == 0) throw new IllegalArgumentException();
                } catch (Throwable t) {
                    smallIcon = Utils.getAppIconAsIntId(context);
                }

                repliedNotification.setSmallIcon(smallIcon)
                        .setContentTitle(pt_title)
                        .setContentText(extras.getString(Constants.PT_INPUT_FEEDBACK))
                        .setVibrate(new long[]{0L})
                        .setTimeoutAfter(Constants.PT_INPUT_TIMEOUT)
                        .setWhen(System.currentTimeMillis())
                        .setAutoCancel(true);

                HashMap <String, Object> mp = new HashMap<>();
                mp.put("Reply", reply);
                mp.put("Platform", "Android");

                cleverTapAPI.pushEvent("Reply Submitted", mp);

                Notification notification = repliedNotification.build();
                notificationManager.notify(notificationId, notification);

                /* Check if Auto Open key is present and not empty, if not present then show feedback and
                auto kill in 3 secs. If present, then launch the App with Dl or Launcher activity.
                The launcher activity will get the reply in extras under the key "pt_reply" */
                if(extras.getString(Constants.PT_INPUT_AUTO_OPEN) != null || extras.getBoolean(Constants.PT_INPUT_AUTO_OPEN)) {
                    //adding delay for launcher
                    try {
                        Thread.sleep(Constants.PT_INPUT_TIMEOUT+200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    Intent launchIntent;

                    if (extras.containsKey(Constants.WZRK_DL)) {
                        launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(intent.getStringExtra(Constants.WZRK_DL)));
                        Utils.setPackageNameFromResolveInfoList(context, launchIntent);
                    } else {
                        launchIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
                        if (launchIntent == null) {
                            return;
                        }
                    }

                    launchIntent.putExtras(extras);

                    //adding reply to extra
                    launchIntent.putExtra("pt_reply", reply);

                    launchIntent.removeExtra(Constants.WZRK_ACTIONS);
                    launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                    context.startActivity(launchIntent);
                }

            } else {
                PTLog.verbose("PushTemplateReceiver: Input is Empty");
            }
        }

    }


    private void handleRatingNotification(Context context, Bundle extras) {

        try {
            int notificationId = extras.getInt("notif_id");
            if (extras.getBoolean("default_dl", false)) {
                notificationManager.cancel(notificationId);

                Intent launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(pt_rating_default_dl));
                launchIntent.putExtras(extras);
                launchIntent.putExtra(Constants.WZRK_DL, pt_rating_default_dl);
                launchIntent.removeExtra(Constants.WZRK_ACTIONS);
                launchIntent.putExtra(Constants.WZRK_FROM_KEY, Constants.WZRK_FROM);
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                return;
            }
            //Set RemoteViews again
            contentViewRating = new RemoteViews(context.getPackageName(), R.layout.rating);
            contentViewRating.setTextViewText(R.id.app_name, context.getResources().getString(R.string.app_name));
            contentViewRating.setTextViewText(R.id.timestamp, Utils.getTimeStamp(context));

            contentViewSmall = new RemoteViews(context.getPackageName(), R.layout.content_view_small);
            contentViewSmall.setTextViewText(R.id.app_name, context.getResources().getString(R.string.app_name));
            contentViewSmall.setTextViewText(R.id.timestamp, Utils.getTimeStamp(context));

            contentViewRating.setTextColor(R.id.app_name, Color.parseColor("#808080"));
            contentViewSmall.setTextColor(R.id.app_name, Color.parseColor("#808080"));
            contentViewRating.setTextColor(R.id.timestamp, Color.parseColor("#808080"));
            contentViewSmall.setTextColor(R.id.timestamp, Color.parseColor("#808080"));

            if (pt_title != null && !pt_title.isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    contentViewRating.setTextViewText(R.id.title, Html.fromHtml(pt_title, Html.FROM_HTML_MODE_LEGACY));
                    contentViewSmall.setTextViewText(R.id.title, Html.fromHtml(pt_title, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    contentViewRating.setTextViewText(R.id.title, Html.fromHtml(pt_title));
                    contentViewSmall.setTextViewText(R.id.title, Html.fromHtml(pt_title));
                }
            }

            if (pt_msg != null && !pt_msg.isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    contentViewSmall.setTextViewText(R.id.msg, Html.fromHtml(pt_msg, Html.FROM_HTML_MODE_LEGACY));
                    contentViewRating.setTextViewText(R.id.msg, Html.fromHtml(pt_msg, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    contentViewSmall.setTextViewText(R.id.msg, Html.fromHtml(pt_msg));
                    contentViewRating.setTextViewText(R.id.msg, Html.fromHtml(pt_msg));
                }
            }
            if (pt_msg_summary != null && !pt_msg_summary.isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    contentViewRating.setTextViewText(R.id.msg, Html.fromHtml(pt_msg_summary, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    contentViewRating.setTextViewText(R.id.msg, Html.fromHtml(pt_msg_summary));
                }
            }

            if (pt_title_clr != null && !pt_title_clr.isEmpty()) {
                contentViewRating.setTextColor(R.id.title, Color.parseColor(pt_title_clr));
                contentViewSmall.setTextColor(R.id.title, Color.parseColor(pt_title_clr));
            }

            if (pt_msg_clr != null && !pt_msg_clr.isEmpty()) {
                contentViewRating.setTextColor(R.id.msg, Color.parseColor(pt_msg_clr));
                contentViewSmall.setTextColor(R.id.msg, Color.parseColor(pt_msg_clr));
            }
            String pt_dl_clicked = deepLinkList.get(0);

            HashMap<String, Object> map = new HashMap<String, Object>();
            if (clicked1 == extras.getBoolean("click1", false)) {
                contentViewRating.setImageViewResource(R.id.star1, R.drawable.filled_star_1);
                map.put("Campaign", extras.getString("wzrk_id"));
                map.put("Rating", 1);
                cleverTapAPI.pushEvent("Rated", map);
                clicked1 = false;

                if (deepLinkList.size() > 0) {
                    pt_dl_clicked = deepLinkList.get(0);
                }
            } else {
                contentViewRating.setImageViewResource(R.id.star1, R.drawable.outline_star_1);
            }
            if (clicked2 == extras.getBoolean("click2", false)) {
                contentViewRating.setImageViewResource(R.id.star1, R.drawable.filled_star_1);
                contentViewRating.setImageViewResource(R.id.star2, R.drawable.filled_star_1);
                map.put("Campaign", extras.getString("wzrk_id"));
                map.put("Rating", 2);
                cleverTapAPI.pushEvent("Rated", map);
                clicked2 = false;
                if (deepLinkList.size() > 1) {
                    pt_dl_clicked = deepLinkList.get(1);
                }else{
                    pt_dl_clicked = deepLinkList.get(0);
                }
            } else {
                contentViewRating.setImageViewResource(R.id.star2, R.drawable.outline_star_1);
            }
            if (clicked3 == extras.getBoolean("click3", false)) {
                contentViewRating.setImageViewResource(R.id.star1, R.drawable.filled_star_1);
                contentViewRating.setImageViewResource(R.id.star2, R.drawable.filled_star_1);
                contentViewRating.setImageViewResource(R.id.star3, R.drawable.filled_star_1);
                map.put("Campaign", extras.getString("wzrk_id"));
                map.put("Rating", 3);
                cleverTapAPI.pushEvent("Rated", map);
                clicked3 = false;
                if (deepLinkList.size() > 2) {
                    pt_dl_clicked = deepLinkList.get(2);
                }else{
                    pt_dl_clicked = deepLinkList.get(0);
                }
            } else {
                contentViewRating.setImageViewResource(R.id.star3, R.drawable.outline_star_1);
            }
            if (clicked4 == extras.getBoolean("click4", false)) {
                contentViewRating.setImageViewResource(R.id.star1, R.drawable.filled_star_1);
                contentViewRating.setImageViewResource(R.id.star2, R.drawable.filled_star_1);
                contentViewRating.setImageViewResource(R.id.star3, R.drawable.filled_star_1);
                contentViewRating.setImageViewResource(R.id.star4, R.drawable.filled_star_1);
                map.put("Campaign", extras.getString("wzrk_id"));
                map.put("Rating", 4);
                cleverTapAPI.pushEvent("Rated", map);
                clicked4 = false;
                if (deepLinkList.size() > 3) {
                    pt_dl_clicked = deepLinkList.get(3);
                }else{
                    pt_dl_clicked = deepLinkList.get(0);
                }
            } else {
                contentViewRating.setImageViewResource(R.id.star4, R.drawable.outline_star_1);
            }
            if (clicked5 == extras.getBoolean("click5", false)) {
                contentViewRating.setImageViewResource(R.id.star1, R.drawable.filled_star_1);
                contentViewRating.setImageViewResource(R.id.star2, R.drawable.filled_star_1);
                contentViewRating.setImageViewResource(R.id.star3, R.drawable.filled_star_1);
                contentViewRating.setImageViewResource(R.id.star4, R.drawable.filled_star_1);
                contentViewRating.setImageViewResource(R.id.star5, R.drawable.filled_star_1);
                map.put("Campaign", extras.getString("wzrk_id"));
                map.put("Rating", 5);
                cleverTapAPI.pushEvent("Rated", map);
                clicked5 = false;
                if (deepLinkList.size() > 4) {
                    pt_dl_clicked = deepLinkList.get(4);
                }else{
                    pt_dl_clicked = deepLinkList.get(0);
                }
            } else {
                contentViewRating.setImageViewResource(R.id.star5, R.drawable.outline_star_1);
            }

            Bundle metaData;
            try {
                PackageManager pm = context.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
                metaData = ai.metaData;
                String x = Utils._getManifestStringValueForKey(metaData, Constants.LABEL_NOTIFICATION_ICON);
                if (x == null) throw new IllegalArgumentException();
                smallIcon = context.getResources().getIdentifier(x, "drawable", context.getPackageName());
                if (smallIcon == 0) throw new IllegalArgumentException();
            } catch (Throwable t) {
                smallIcon = Utils.getAppIconAsIntId(context);
            }

            NotificationCompat.Builder notificationBuilder;
            if (requiresChannelId) {
                notificationBuilder = new NotificationCompat.Builder(context, channelId);
            } else {
                notificationBuilder = new NotificationCompat.Builder(context);
            }

            if (notificationManager != null) {
                //Use the Builder to build notification
                notificationBuilder.setSmallIcon(smallIcon)
                        .setCustomContentView(contentViewSmall)
                        .setCustomBigContentView(contentViewRating)
                        .setContentTitle("Custom Notification")
                        .setAutoCancel(true);

                Notification notification = notificationBuilder.build();
                notificationManager.notify(notificationId, notification);
                Utils.loadIntoGlide(context, R.id.small_icon, pt_img_small, contentViewSmall, notification, notificationId);
                Utils.loadIntoGlide(context, R.id.small_icon, pt_img_small, contentViewRating, notification, notificationId);
                Thread.sleep(1000);
                notificationManager.cancel(notificationId);
                Toast.makeText(context, "Thank you for your feedback", Toast.LENGTH_SHORT).show();

                Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
                context.sendBroadcast(it);

                Intent launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(pt_dl_clicked));
                launchIntent.putExtras(extras);
                launchIntent.putExtra(Constants.WZRK_DL, pt_dl_clicked);
                launchIntent.removeExtra(Constants.WZRK_ACTIONS);
                launchIntent.putExtra(Constants.WZRK_FROM_KEY, Constants.WZRK_FROM);
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
            }

        } catch (Throwable t) {
            PTLog.verbose("Error creating rating notification ", t);
        }


    }


    private void handleProductDisplayNotification(Context context, Bundle extras) {
        try {
            int notificationId = extras.getInt("notif_id");
            if (buynow == extras.getBoolean("buynow", false)) {
                notificationManager.cancel(notificationId);
                context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)); // close the notification drawer
                String dl = extras.getString(Constants.PT_BUY_NOW_DL, deepLinkList.get(0));
                Intent launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(dl));
                launchIntent.putExtras(extras);
                launchIntent.putExtra(Constants.WZRK_DL, dl);
                launchIntent.removeExtra(Constants.WZRK_ACTIONS);
                launchIntent.putExtra(Constants.WZRK_FROM_KEY, Constants.WZRK_FROM);
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(launchIntent);
                return;
            }
            contentViewBig = new RemoteViews(context.getPackageName(), R.layout.product_display_template);
            contentViewBig.setTextViewText(R.id.app_name, context.getResources().getString(R.string.app_name));
            contentViewBig.setTextViewText(R.id.timestamp, Utils.getTimeStamp(context));

            contentViewSmall = new RemoteViews(context.getPackageName(), R.layout.content_view_small);
            contentViewSmall.setTextViewText(R.id.app_name, context.getResources().getString(R.string.app_name));
            contentViewSmall.setTextViewText(R.id.timestamp, Utils.getTimeStamp(context));

            contentViewBig.setTextColor(R.id.app_name, Color.parseColor("#808080"));
            contentViewSmall.setTextColor(R.id.app_name, Color.parseColor("#808080"));
            contentViewBig.setTextColor(R.id.timestamp, Color.parseColor("#808080"));
            contentViewSmall.setTextColor(R.id.timestamp, Color.parseColor("#808080"));

            if (!bigTextList.isEmpty()) {
                contentViewBig.setTextViewText(R.id.product_name, bigTextList.get(0));

            }

            if (!smallTextList.isEmpty()) {
                contentViewBig.setTextViewText(R.id.product_description, smallTextList.get(0));

            }

            if (!priceList.isEmpty()) {
                contentViewBig.setTextViewText(R.id.product_price, priceList.get(0));

            }

            if (pt_title != null && !pt_title.isEmpty()) {
                contentViewBig.setTextViewText(R.id.title, pt_title);
                contentViewSmall.setTextViewText(R.id.title, pt_title);
            }

            if (pt_msg != null && !pt_msg.isEmpty()) {
                contentViewBig.setTextViewText(R.id.msg, pt_msg);
                contentViewSmall.setTextViewText(R.id.msg, pt_msg);
            }

            if (pt_title_clr != null && !pt_title_clr.isEmpty()) {
                contentViewBig.setTextColor(R.id.title, Color.parseColor(pt_title_clr));
                contentViewSmall.setTextColor(R.id.title, Color.parseColor(pt_title_clr));
            }

            if (pt_msg_clr != null && !pt_msg_clr.isEmpty()) {
                contentViewBig.setTextColor(R.id.msg, Color.parseColor(pt_msg_clr));
                contentViewSmall.setTextColor(R.id.msg, Color.parseColor(pt_msg_clr));
            }

            String imageUrl = "", dl = "";
            if (img1 != extras.getBoolean("img1", false)) {
                imageUrl = imageList.get(0);
                if (!bigTextList.isEmpty()) {
                    contentViewBig.setTextViewText(R.id.product_name, bigTextList.get(0));
                    contentViewBig.setTextViewText(R.id.product_description, smallTextList.get(0));
                    contentViewBig.setTextViewText(R.id.product_price, priceList.get(0));
                }
                img1 = false;
                dl = deepLinkList.get(0);
            }
            if (img2 != extras.getBoolean("img2", false)) {
                imageUrl = imageList.get(1);
                if (!bigTextList.isEmpty()) {
                    contentViewBig.setTextViewText(R.id.product_name, bigTextList.get(1));
                    contentViewBig.setTextViewText(R.id.product_description, smallTextList.get(1));
                    contentViewBig.setTextViewText(R.id.product_price, priceList.get(1));
                }
                img2 = false;
                dl = deepLinkList.get(1);
            }
            if (img3 != extras.getBoolean("img3", false)) {
                imageUrl = imageList.get(2);
                if (!bigTextList.isEmpty()) {
                    contentViewBig.setTextViewText(R.id.product_name, bigTextList.get(2));
                    contentViewBig.setTextViewText(R.id.product_description, smallTextList.get(2));
                    contentViewBig.setTextViewText(R.id.product_price, priceList.get(2));
                }
                img3 = false;
                dl = deepLinkList.get(2);
            }
            PendingIntent pIntent = null;

            if (bigimage == extras.getBoolean("bigimage", false)) {
                bigimage = false;
            }


            int requestCode1 = extras.getInt("pt_reqcode1");
            int requestCode2 = extras.getInt("pt_reqcode2");
            int requestCode3 = extras.getInt("pt_reqcode3");

            Intent notificationIntent1 = new Intent(context, PushTemplateReceiver.class);
            notificationIntent1.putExtra("img1", true);
            notificationIntent1.putExtra("notif_id", notificationId);
            notificationIntent1.putExtra(Constants.PT_BUY_NOW_DL, deepLinkList.get(0));
            notificationIntent1.putExtra("pt_reqcode1", requestCode1);
            notificationIntent1.putExtra("pt_reqcode2", requestCode2);
            notificationIntent1.putExtra("pt_reqcode3", requestCode3);
            notificationIntent1.putExtras(extras);
            PendingIntent contentIntent1 = PendingIntent.getBroadcast(context, requestCode1, notificationIntent1, 0);
            contentViewBig.setOnClickPendingIntent(R.id.small_image1, contentIntent1);

            Intent notificationIntent2 = new Intent(context, PushTemplateReceiver.class);
            notificationIntent2.putExtra("img2", true);
            notificationIntent2.putExtra("notif_id", notificationId);
            notificationIntent2.putExtra(Constants.PT_BUY_NOW_DL, deepLinkList.get(1));
            notificationIntent2.putExtra("pt_reqcode1", requestCode1);
            notificationIntent2.putExtra("pt_reqcode2", requestCode2);
            notificationIntent2.putExtra("pt_reqcode3", requestCode3);
            notificationIntent2.putExtras(extras);
            PendingIntent contentIntent2 = PendingIntent.getBroadcast(context, requestCode2, notificationIntent2, 0);
            contentViewBig.setOnClickPendingIntent(R.id.small_image2, contentIntent2);

            Intent notificationIntent3 = new Intent(context, PushTemplateReceiver.class);
            notificationIntent3.putExtra("img3", true);
            notificationIntent3.putExtra("notif_id", notificationId);
            notificationIntent3.putExtra(Constants.PT_BUY_NOW_DL, deepLinkList.get(2));
            notificationIntent3.putExtra("pt_reqcode1", requestCode1);
            notificationIntent3.putExtra("pt_reqcode2", requestCode2);
            notificationIntent3.putExtra("pt_reqcode3", requestCode3);
            notificationIntent3.putExtras(extras);
            PendingIntent contentIntent3 = PendingIntent.getBroadcast(context, requestCode3, notificationIntent3, 0);
            contentViewBig.setOnClickPendingIntent(R.id.small_image3, contentIntent3);

            Intent notificationIntent4 = new Intent(context, PushTemplateReceiver.class);
            notificationIntent4.putExtra("img1", true);
            notificationIntent4.putExtra("notif_id", notificationId);
            notificationIntent4.putExtra(Constants.PT_BUY_NOW_DL, dl);
            notificationIntent4.putExtra("pt_reqcode1", requestCode1);
            notificationIntent4.putExtra("pt_reqcode2", requestCode2);
            notificationIntent4.putExtra("pt_reqcode3", requestCode3);
            notificationIntent4.putExtra("buynow", true);
            notificationIntent4.putExtras(extras);
            PendingIntent contentIntent4 = PendingIntent.getBroadcast(context, new Random().nextInt(), notificationIntent4, 0);
            contentViewBig.setOnClickPendingIntent(R.id.action_button, contentIntent4);

            Bundle metaData;
            try {
                PackageManager pm = context.getPackageManager();
                ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
                metaData = ai.metaData;
                String x = Utils._getManifestStringValueForKey(metaData, Constants.LABEL_NOTIFICATION_ICON);
                if (x == null) throw new IllegalArgumentException();
                smallIcon = context.getResources().getIdentifier(x, "drawable", context.getPackageName());
                if (smallIcon == 0) throw new IllegalArgumentException();
            } catch (Throwable t) {
                smallIcon = Utils.getAppIconAsIntId(context);
            }

            NotificationCompat.Builder notificationBuilder;
            if (requiresChannelId) {
                notificationBuilder = new NotificationCompat.Builder(context, channelId);
            } else {
                notificationBuilder = new NotificationCompat.Builder(context);
            }

            if (notificationManager != null) {
                //Use the Builder to build notification
                notificationBuilder.setSmallIcon(smallIcon)
                        .setCustomContentView(contentViewBig)
                        .setCustomBigContentView(contentViewBig)
                        .setContentTitle("Custom Notification")
                        .setContentIntent(pIntent)
                        .setAutoCancel(true);

                Notification notification = notificationBuilder.build();
                notificationManager.notify(notificationId, notification);
                for (int index = 0; index < imageList.size(); index++) {
                    if (index == 0) {
                        Utils.loadIntoGlide(context, R.id.small_image1, imageList.get(0), contentViewBig, notification, notificationId);
                    } else if (index == 1) {
                        Utils.loadIntoGlide(context, R.id.small_image2, imageList.get(1), contentViewBig, notification, notificationId);
                    } else if (index == 2) {
                        Utils.loadIntoGlide(context, R.id.small_image3, imageList.get(2), contentViewBig, notification, notificationId);
                    }
                }
                Utils.loadIntoGlide(context, R.id.big_image, imageUrl, contentViewBig, notification, notificationId);
            }

        } catch (Throwable t) {
            PTLog.verbose("Error creating rating notification ", t);
        }
    }

    private void handleFiveCTANotification(Context context, Bundle extras) {
        String dl = null;

        int notificationId = extras.getInt("notif_id");
        if (cta1 == extras.getBoolean("cta1")) {
            dl = deepLinkList.get(0);
        }
        if (cta2 == extras.getBoolean("cta2")) {
            dl = deepLinkList.get(1);
        }
        if (cta3 == extras.getBoolean("cta3")) {
            dl = deepLinkList.get(2);
        }
        if (cta4 == extras.getBoolean("cta4")) {
            dl = deepLinkList.get(3);
        }
        if (cta5 == extras.getBoolean("cta5")) {
            dl = deepLinkList.get(4);
        }
        if (close == extras.getBoolean("close")) {
            notificationManager.cancel(notificationId);
            context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
            return;

        }

        Intent launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(dl));
        launchIntent.putExtras(extras);
        launchIntent.putExtra(Constants.WZRK_DL, dl);
        launchIntent.removeExtra(Constants.WZRK_ACTIONS);
        launchIntent.putExtra(Constants.WZRK_FROM_KEY, Constants.WZRK_FROM);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(launchIntent);
        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
    }
    private void renderVideoNotification(final Context context, Bundle extras) {
        try {
            contentViewBig = new RemoteViews(context.getPackageName(), R.layout.image_only_big);
            contentViewBig.setTextViewText(R.id.app_name, Utils.getApplicationName(context));
            contentViewBig.setTextViewText(R.id.timestamp, Utils.getTimeStamp(context));

            contentViewSmall = new RemoteViews(context.getPackageName(), R.layout.content_view_small);
            contentViewSmall.setTextViewText(R.id.app_name, Utils.getApplicationName(context));
            contentViewSmall.setTextViewText(R.id.timestamp, Utils.getTimeStamp(context));

            contentViewBig.setTextColor(R.id.app_name, ContextCompat.getColor(context,R.color.gray));
            contentViewSmall.setTextColor(R.id.app_name, ContextCompat.getColor(context,R.color.gray));
            contentViewBig.setTextColor(R.id.timestamp, ContextCompat.getColor(context,R.color.gray));
            contentViewSmall.setTextColor(R.id.timestamp, ContextCompat.getColor(context,R.color.gray));

            if (pt_title != null && !pt_title.isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    contentViewBig.setTextViewText(R.id.title, Html.fromHtml(pt_title, Html.FROM_HTML_MODE_LEGACY));
                    contentViewSmall.setTextViewText(R.id.title, Html.fromHtml(pt_title, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    contentViewBig.setTextViewText(R.id.title, Html.fromHtml(pt_title));
                    contentViewSmall.setTextViewText(R.id.title, Html.fromHtml(pt_title));
                }
            }

            if (pt_msg != null && !pt_msg.isEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    contentViewBig.setTextViewText(R.id.msg, Html.fromHtml(pt_msg, Html.FROM_HTML_MODE_LEGACY));
                    contentViewSmall.setTextViewText(R.id.msg, Html.fromHtml(pt_msg, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    contentViewBig.setTextViewText(R.id.msg, Html.fromHtml(pt_msg));
                    contentViewSmall.setTextViewText(R.id.msg, Html.fromHtml(pt_msg));
                }
            }


                contentViewBig.setInt(R.id.image_only_big_linear_layout, "setBackgroundColor", Color.parseColor("#000"));
                contentViewSmall.setInt(R.id.content_view_small, "setBackgroundColor", Color.parseColor("#000"));


            if (pt_title_clr != null && !pt_title_clr.isEmpty()) {
                contentViewBig.setTextColor(R.id.title, Color.parseColor(pt_title_clr));
                contentViewSmall.setTextColor(R.id.title, Color.parseColor(pt_title_clr));
            }

            if (pt_msg_clr != null && !pt_msg_clr.isEmpty()) {
                contentViewBig.setTextColor(R.id.msg, Color.parseColor(pt_msg_clr));
                contentViewSmall.setTextColor(R.id.msg, Color.parseColor(pt_msg_clr));
            }



            Intent launchIntent = new Intent(context, SecondActivity.class);
            launchIntent.putExtras(extras);
            if (Constants.WZRK_VIDEO != null ) {
                launchIntent.putExtra("bundle", extras);
            }
            launchIntent.removeExtra(Constants.WZRK_ACTIONS);
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pIntent = PendingIntent.getBroadcast(context, (int) System.currentTimeMillis(),
                    launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder notificationBuilder;
            if (requiresChannelId) {
                notificationBuilder = new NotificationCompat.Builder(context, channelId);
            } else {
                notificationBuilder = new NotificationCompat.Builder(context);
            }

            notificationBuilder.setSmallIcon(smallIcon)
                    .setCustomContentView(contentViewSmall)
                    .setCustomBigContentView(contentViewBig)
                    .setContentTitle(pt_title)
                    .setContentIntent(pIntent)
                    .setVibrate(new long[]{0L})
                    .setAutoCancel(true);

            Notification notification = notificationBuilder.build();



        } catch (Throwable t) {
            PTLog.verbose("Error creating image only notification", t);
        }
    }


}
