/*
 * (c) 2020 Open Source Geospatial Foundation - all rights reserved This code is licensed under the
 * GPL 2.0 license, available at the root application directory.
 */
package org.geoserver.rest.v2.controller;

import static org.springframework.http.HttpStatus.*;

import org.springframework.http.HttpStatus;

/** API exception, including HTTP status code */
public class ApiException extends RuntimeException {
    /** serialVersionUID */
    private static final long serialVersionUID = 5762645820684796082L;

    private final HttpStatus status;

    public ApiException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public ApiException(String message, HttpStatus status, Throwable t) {
        super(message, t);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static ApiException notFound() {
        return new ApiException("Not found", NOT_FOUND);
    }
}
