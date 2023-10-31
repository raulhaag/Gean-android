package ar.rulosoft.gean;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import androidx.core.content.FileProvider;

public class Updates {

    static File path;
    static String version;

    public static void checkUpdates(Context context) throws IOException {
        if (new File(path, "version").exists()) {
            version = InetTools.get("https://raw.githubusercontent.com/raulhaag/gean/master/version", new HashMap<>(), new ArrayList<>());
            String[] r_version = version.split("\\.");
            String[] l_version = new BufferedReader(new FileReader(new File(path, "version"))).readLine().split("\\.");

            int ri_version = Integer.parseInt(r_version[0]) * 100000000 + Integer.parseInt(r_version[1]) * 100000 + Integer.parseInt(r_version[2]);
            int li_version = Integer.parseInt(l_version[0]) * 100000000 + Integer.parseInt(l_version[1]) * 100000 + Integer.parseInt(l_version[2]);
            if(r_version.length > 3){
                if(BuildConfig.VERSION_CODE < Integer.parseInt(l_version[3])){
            String docpatch = Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_DOCUMENTS).getAbsolutePath();
            File dir = new File(docpatch,"Gean");
            dir.mkdirs();
            File up = new File(dir, "update.apk");
            if (up.exists()) {
                up.delete();
            }
            InetTools.download("https://github.com/raulhaag/Gean-android/raw/master/app/build/outputs/apk/debug/app-universal-debug.apk", up);
            Uri upUri = FileProvider.getUriForFile(context, context.getApplicationContext().getPackageName() + ".provider", up);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(upUri, "application/vnd.android.package-archive");
            List<ResolveInfo> resInfoList = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                context.grantUriPermission(packageName, upUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
               }
            }
            if(ri_version <= li_version){
                Log.i("Version gean", version);
                return;
            }

            Log.i("Update gean", version);
        }
        File up = new File(path, "update.zip");
        if (up.exists()) {
            up.delete();
        }
        InetTools.download("https://github.com/raulhaag/gean/archive/refs/heads/master.zip", up);
        unzipUpdate(up.getAbsolutePath(), path.getAbsolutePath());
    }

    public static void unzipUpdate(String zipFilePath, String destDirectory) throws IOException {
        File destDir = new File(destDirectory);
        if (!destDir.exists()) {
            destDir.mkdir();
        }
        byte[] bytesIn = new byte[4096];
        ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
        ZipEntry entry = zipIn.getNextEntry();
        while (entry != null) {
            String filePath = destDirectory + File.separator + entry.getName().replace("gean-master/", "");
            if (!entry.isDirectory()) {
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
                int read = 0;
                while ((read = zipIn.read(bytesIn)) != -1) {
                    bos.write(bytesIn, 0, read);
                }
                bos.close();
            } else {
                try {
                    File dir = new File(filePath);
                    dir.mkdirs();
                }catch (Exception e){
                    //ignore
                }
            }
            zipIn.closeEntry();
            entry = zipIn.getNextEntry();
        }
        zipIn.close();
    }
}
