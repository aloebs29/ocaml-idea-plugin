package jpsplugin.com.reason;

import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.SystemInfo;

import java.io.*;
import java.net.*;
import java.nio.file.StandardCopyOption;

import org.jetbrains.annotations.NotNull;

public class WGet {
    private static final Log LOG = Log.create("wget");

    private static final int BUFFER_SIZE = 1024;

    private WGet() {
    }

    public static boolean apply(@NotNull String urlString, @NotNull File targetFile, @NotNull ProgressIndicator indicator, double totalBytes) {
        return apply(urlString, targetFile, indicator, totalBytes, true);
    }

    /**
     * @param notifyOnError when {@code false}, a failure is only logged - the caller is expected to report it,
     *                      which avoids repeating the same balloon for a download that is known to never succeed
     */
    public static boolean apply(@NotNull String urlString, @NotNull File targetFile, @NotNull ProgressIndicator indicator, double totalBytes, boolean notifyOnError) {
        File partFile = new File(targetFile.getPath() + ".part");
        try {
            if (partFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                partFile.delete();
            }

            //noinspection ResultOfMethodCallIgnored
            partFile.createNewFile();

            FileOutputStream partFileOut = new FileOutputStream(partFile);

            java.net.URL url = URI.create(urlString).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoOutput(true);

            connection.setConnectTimeout(240 * 1000);
            connection.setReadTimeout(240 * 1000);

            InputStream inputStream = connection.getInputStream();

            double totalBytesDownloaded = 0.0;

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead = inputStream.read(buffer);
            while (bytesRead >= 0) {
                if (totalBytes > 0.0) {
                    indicator.setFraction(totalBytesDownloaded / totalBytes);
                }
                totalBytesDownloaded += bytesRead;

                partFileOut.write(buffer, 0, bytesRead);
                bytesRead = inputStream.read(buffer);
            }

            connection.disconnect();
            partFileOut.close();
            inputStream.close();

            java.nio.file.Files.move(partFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
            if (!SystemInfo.isWindows) {
                //noinspection ResultOfMethodCallIgnored
                targetFile.setExecutable(true);
            }

            LOG.info(targetFile.getName() + " downloaded to " + targetFile.toPath().getParent());
            Notifications.Bus.notify(new ORNotification("OCaml", "Downloaded " + targetFile, NotificationType.INFORMATION));

            return true;
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            partFile.delete(); // do not leave a truncated download behind
            LOG.info("Can't download " + targetFile, e);
            if (notifyOnError) {
                Notifications.Bus.notify(new ORNotification("OCaml", "Can't download " + targetFile + "\n" + e, NotificationType.ERROR));
            }
            return false;
        }
    }
}
