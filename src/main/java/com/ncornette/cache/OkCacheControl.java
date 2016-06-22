package com.ncornette.cache;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.CacheControl;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by nic on 21/06/16.
 */
public class OkCacheControl {

    /**
     * Created by nic on 20/06/16.
     */
    public interface NetworkMonitor {

        boolean isOnline();
    }

    public interface MaxAgeControl {
        /**
         * @return max-age in seconds
         */
        long getMaxAge();
    }

    public static Builder on(OkHttpClient.Builder okBuilder) {
        Builder builder = new Builder(okBuilder);
        return builder;
    }


    public static class Builder {
        private NetworkMonitor networkMonitor;
        private long maxAgeValue;
        private TimeUnit maxAgeUnit;
        private OkHttpClient.Builder okBuilder;
        private MaxAgeControl maxAgeControl;

        private Builder(OkHttpClient.Builder okBuilder) {
            this.okBuilder = okBuilder;
        }

        public Builder overrideServerCachePolicy(long timeValue, TimeUnit unit) {
            this.maxAgeControl = null;
            this.maxAgeValue = timeValue;
            this.maxAgeUnit = unit;
            return this;
        }

        public Builder overrideServerCachePolicy(MaxAgeControl maxAgeControl) {
            this.maxAgeUnit = null;
            this.maxAgeControl = maxAgeControl;
            return this;
        }

        public Builder forceCacheWhenOffline(NetworkMonitor networkMonitor) {
            this.networkMonitor = networkMonitor;
            return this;
        }

        public OkHttpClient.Builder apply() {
            if (networkMonitor == null && maxAgeUnit == null && maxAgeControl == null) {
                return okBuilder;
            }

            if (maxAgeUnit != null) {
                maxAgeControl = new StaticMaxAgeControl(maxAgeUnit, maxAgeValue);
            }

            ResponseHandler responseHandler;
            if (maxAgeControl != null) {
                responseHandler = new CachePolicyResponseHandler(maxAgeControl);
            } else {
                responseHandler = new ResponseHandler();
            }

            RequestHandler requestHandler;
            if (networkMonitor != null) {
                requestHandler = new NetworkMonitorRequestHandler(networkMonitor);
            } else {
                requestHandler = new RequestHandler();
            }

            Interceptor cacheControlInterceptor = getCacheControlInterceptor(
                    requestHandler, responseHandler);

            okBuilder.addNetworkInterceptor(cacheControlInterceptor);

            if (networkMonitor != null) {
                okBuilder.addInterceptor(cacheControlInterceptor);
            }

            return okBuilder;
        }

        private static class StaticMaxAgeControl implements MaxAgeControl {
            private TimeUnit maxAgeUnit;
            private long maxAgeValue;

            private StaticMaxAgeControl(TimeUnit maxAgeUnit, long maxAgeValue) {
                this.maxAgeUnit = maxAgeUnit;
                this.maxAgeValue = maxAgeValue;
            }

            @Override
            public long getMaxAge() {
                return maxAgeUnit.toSeconds(maxAgeValue);
            }
        }

        private static class CachePolicyResponseHandler extends ResponseHandler {
            private MaxAgeControl maxAgeControl;

            private CachePolicyResponseHandler(MaxAgeControl maxAgeControl) {
                this.maxAgeControl = maxAgeControl;
            }

            @Override
            public Response newResponse(Response response) {
                return response.newBuilder()
                        .removeHeader("Pragma")
                        .removeHeader("Cache-Control")
                        .header("Cache-Control", "max-age=" + maxAgeControl.getMaxAge())
                    .build();
            }
        }

        private static class NetworkMonitorRequestHandler extends RequestHandler {
            private NetworkMonitor networkMonitor;

            private NetworkMonitorRequestHandler(NetworkMonitor networkMonitor) {
                this.networkMonitor = networkMonitor;
            }

            @Override
            public Request newRequest(Request request) {
                Request.Builder newBuilder = request.newBuilder();
                if (!networkMonitor.isOnline()) {
                    // To be used with Application Interceptor to use Expired cache
                    newBuilder.cacheControl(CacheControl.FORCE_CACHE);
                }
                return newBuilder.build();
            }
        }
    }

    private static Interceptor getCacheControlInterceptor(final RequestHandler requestHandler,
                                                          final ResponseHandler responseHandler) {
        return new Interceptor() {
            @Override public Response intercept(Chain chain) throws IOException {
                Request originalRequest = chain.request();
                Request request = requestHandler.newRequest(originalRequest);

                Response originalResponse = chain.proceed(request);

                return responseHandler.newResponse(originalResponse);
            }
        };
    }

    private static class ResponseHandler {
        public Response newResponse(Response response) {
            return response;
        }
    }

    private static class RequestHandler {
        public Request newRequest(Request request) {
            return request;
        }
    }
}
