package com.intel.cosbench.api.AlluxioStor;

import com.intel.cosbench.api.storage.*;

public class AlluxioStorageFactory implements StorageAPIFactory {

    @Override
    public String getStorageName() {
        return "alluxio";
    }

    @Override
    public StorageAPI getStorageAPI() {
        return new AlluxioStorage();
    }

}
