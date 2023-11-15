package com.intel.cosbench.client.AlluxioStor;

public interface AlluxioConstants {
    // --------------------------------------------------------------------------
    // CONNECTION
    // --------------------------------------------------------------------------

    String CONN_TIMEOUT_KEY = "timeout";
    int CONN_TIMEOUT_DEFAULT = 3;
    // --------------------------------------------------------------------------
    // ENDPOINT
    // --------------------------------------------------------------------------
    String ENDPOINT_KEY = "endpoint";
    String ENDPOINT_DEFAULT = "http://alluxio:39999";

    // --------------------------------------------------------------------------
    // Authorization
    // --------------------------------------------------------------------------

    String AUTHORIZATION_KEY = "Authorization";

    // --------------------------------------------------------------------------
    // CONTEXT NEEDS FROM AUTH MODULE
    // --------------------------------------------------------------------------
    String AlluxioCLIENT_KEY = "alluxioclient";

}
