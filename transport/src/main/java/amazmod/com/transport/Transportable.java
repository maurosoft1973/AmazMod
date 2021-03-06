package amazmod.com.transport;

import android.os.Bundle;

import com.huami.watch.transport.DataBundle;

public abstract class Transportable {

    public abstract DataBundle toDataBundle(DataBundle dataBundle);

    public abstract Bundle toBundle();

    public DataBundle toDataBundle() {
        DataBundle dataBundle = new DataBundle();
        return toDataBundle(dataBundle);
    }

}
