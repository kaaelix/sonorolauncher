package id.local.wuwaconnect;

import android.os.Bundle;
import android.os.ParcelFileDescriptor;

interface IShellService {
    Bundle exec(String script);
    boolean writeFile(String destPath, in ParcelFileDescriptor pfd);
    void destroy();
}
