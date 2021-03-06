package org.ops4j.pax.exam.acceptance.rest.restassured;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import java.util.function.Function;
import java.util.function.Supplier;
import org.ops4j.pax.exam.acceptance.ClientConfiguration;
import org.ops4j.pax.exam.acceptance.SessionSpec;
import org.ops4j.pax.exam.acceptance.rest.api.RestClient;
import org.ops4j.pax.exam.acceptance.rest.api.RestRequest;
import org.ops4j.pax.exam.acceptance.rest.api.RestResult;

import static io.restassured.RestAssured.given;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


public class RestClientImpl implements RestClient {

    private final SessionSpec env;
    private final ClientConfiguration clientConfig;

    // TODO: Setup Restassured here properly.
    public RestClientImpl(SessionSpec sessionSpec, ClientConfiguration clientConfiguration) {
        this.env = sessionSpec;
            this.clientConfig = clientConfiguration;
            RestAssured.port = sessionSpec.getPort();

        /**
        RestAssured.config = RestAssuredConfig.config().objectMapperConfig(io.restassured.config.ObjectMapperConfig
                .objectMapperConfig().jackson2ObjectMapperFactory(new Jackson2ObjectMapperFactory() {
                    public ObjectMapper create(Class cls, String charset) {
                        ObjectMapper mapper = new ObjectMapper();
                        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                        return mapper;
                    }
                }));
         **/

    }


    @Override
    public RestResult getWithRetry(RestRequest r) {
        Response res = null;
        int retries = this.env.getRetries() * 3;
        Exception retryException = null;
        for (int i = 0;i<retries;i++) {
            try {
                res = given().auth().basic(clientConfig.getUser(), clientConfig.getPassword()).headers(r.getHeaders()).body(r.getBody()).when().get(r.getPath());
                if (res.statusCode() != 404) {
                    return new RestResultImpl(res, null, retries);
                }
            } catch (Exception e ) {
                // retries..
            	retryException = e;
            }
            try {
            TimeUnit.MILLISECONDS.sleep(200);
            } catch (InterruptedException e) {
                Thread.interrupted();
                return new RestResultImpl(null, e, retries);
            }
        }
        return new RestResultImpl(res, retryException, this.env.getRetries());
    }

    @Override
    public RestResult get(RestRequest r) {
        try {
            Response res = given().auth().basic(clientConfig.getUser(), clientConfig.getPassword()).headers(r.getHeaders()).body(r.getBody()).when().get(r.getPath());
            return new RestResultImpl(res, null, 0);
        } catch (Exception e ) {
            return new RestResultImpl(null, e, 0);
        }
    }

    @Override
    public RestResult post(RestRequest r) {
            try {
                Response res = given().auth().basic(clientConfig.getUser(), clientConfig.getPassword()).headers(r.getHeaders()).body(r.getBody()).when().post(r.getPath());
                return new RestResultImpl(res, null, 0);
            } catch (Exception e ) {
                return new RestResultImpl(null, e, 0);
            }
    }

    @Override
    public RestResult put(RestRequest r) {
        try {
            Response res = given().auth().basic(clientConfig.getUser(), clientConfig.getPassword()).headers(r.getHeaders()).body(r.getBody()).when().put(r.getPath());
            return new RestResultImpl(res, null, 0);
        } catch (Exception e ) {
            return new RestResultImpl(null, e, 0);
        }
    }

    private class RestResultImpl implements RestResult {
        private final Response response;
		private Exception e;
		private int retries;

        public RestResultImpl(Response res, Exception e, int retries) {
            this.response = res;
			this.e = e;
			this.retries = retries;
        }

        @Override
        public RestResult then() {
            return this;
        }

        @Override
        public void statusCode(int status) {
        	if (response == null) {
        		if (e != null) {
        			throw new RuntimeException("failed after maximum retries "+retries, e);
        		} else {
        			throw new IllegalStateException("no response");
        		}
        	}
            response.then().statusCode(status);
        }
    }
}
