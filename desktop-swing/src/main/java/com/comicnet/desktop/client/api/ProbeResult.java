package com.comicnet.desktop.client.api;

public record ProbeResult(boolean apiUp, boolean databaseReady, String service, String requestId) {}
