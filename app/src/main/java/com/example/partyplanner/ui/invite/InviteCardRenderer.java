package com.example.partyplanner.ui.invite;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import com.example.partyplanner.R;
import com.example.partyplanner.data.EventWithDetails;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;

public final class InviteCardRenderer {

    private InviteCardRenderer() {}

    // Рендерим layout в bitmap
    public static Bitmap renderToBitmap(Context ctx, EventWithDetails e) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.invite_card, null, false);

        TextView tvTitle = v.findViewById(R.id.invTitle);
        TextView tvDate  = v.findViewById(R.id.invDate);
        TextView tvAddr  = v.findViewById(R.id.invAddress);
        TextView tvOrg   = v.findViewById(R.id.invOrganizer);

        tvTitle.setText(e.event.title);
        tvDate.setText(DateFormat.getDateTimeInstance().format(new Date(e.event.dateTime)));
        tvAddr.setText(e.event.address);
        tvOrg.setText("Organizer: " + (e.organizer != null ? e.organizer.name : "—"));

        // шаблон (очень просто: меняем заголовок/эмодзи)
        applyTemplate(v, e.event.inviteTemplateId);

        int widthSpec = View.MeasureSpec.makeMeasureSpec(dp(ctx, 360), View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        v.measure(widthSpec, heightSpec);
        v.layout(0, 0, v.getMeasuredWidth(), v.getMeasuredHeight());

        Bitmap bmp = Bitmap.createBitmap(v.getMeasuredWidth(), v.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        v.draw(c);
        return bmp;
    }

    // Сохраняем bitmap как PNG и отдаём Uri
    public static Uri savePngAndGetUri(Context ctx, Bitmap bmp, String fileNameNoExt) {
        String fileName = fileNameNoExt + ".png";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PartyPlanner");

                Uri uri = ctx.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return null;

                try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                }
                return uri;
            } else {
                File dir = new File(ctx.getCacheDir(), "invites");
                if (!dir.exists()) dir.mkdirs();

                File file = new File(dir, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }

                // authority должен совпадать с манифестом
                return FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", file);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private static void applyTemplate(View v, String templateId) {
        TextView badge = v.findViewById(R.id.invBadge);
        if (badge == null) return;

        if ("birthday".equalsIgnoreCase(templateId)) badge.setText("🎉 Birthday");
        else if ("formal".equalsIgnoreCase(templateId)) badge.setText("🖤 Formal");
        else if ("movie".equalsIgnoreCase(templateId)) badge.setText("🎬 Movie night");
        else if ("bbq".equalsIgnoreCase(templateId)) badge.setText("🔥 BBQ");
        else if ("kids".equalsIgnoreCase(templateId)) badge.setText("🧸 Kids");
        else badge.setText("✨ Invitation");

        View root = v.findViewById(R.id.invRoot);

        if ("birthday".equalsIgnoreCase(templateId)) root.setBackgroundResource(R.drawable.inv_bg_birthday);
        else if ("formal".equalsIgnoreCase(templateId)) root.setBackgroundResource(R.drawable.inv_bg_formal);
        else root.setBackgroundResource(R.drawable.inv_bg_classic);
    }

    private static int dp(Context ctx, int dp) {
        float d = ctx.getResources().getDisplayMetrics().density;
        return (int) (dp * d + 0.5f);
    }
}