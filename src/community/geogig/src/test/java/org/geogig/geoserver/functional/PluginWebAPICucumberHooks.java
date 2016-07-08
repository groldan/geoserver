/* (c) 2016 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geogig.geoserver.functional;

import static com.google.common.base.Preconditions.checkArgument;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URI;

import org.geogig.web.functional.WebAPICucumberHooks;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.api.plumbing.ResolveGeogigURI;
import org.restlet.data.Form;
import org.restlet.data.Method;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;
import cucumber.runtime.java.StepDefAnnotation;
import cucumber.runtime.java.guice.ScenarioScoped;

/**
 * Extensions to the GeoGig Web API Functional tests. These these are specific to the GeoServer
 * plugin.
 */
@ScenarioScoped
@StepDefAnnotation
public class PluginWebAPICucumberHooks {

    static final String SYSTEM_TEMP_PATH = System.getProperty("java.io.tmpdir");

    public GeoServerFunctionalTestContext context;

    /**
     * Create an instance of this set of Steps with the GeoGig Web API Hooks as a parent. Since you
     * cannot extend a Step Definition class, just inject the one that gets created during the test
     * run and grab the Context. It <i>should</i> be an instance of
     * {@link GeoServerFunctionalTestContext}.
     *
     * @param parent
     */
    @Inject
    public PluginWebAPICucumberHooks(WebAPICucumberHooks parent) {
        if (GeoServerFunctionalTestContext.class.isAssignableFrom(parent.context.getClass())) {
            this.context = GeoServerFunctionalTestContext.class.cast(parent.context);
        }
    }

    /**
     * Extracts the String representation of a JSON object response. The supplied <b>jsonPath</b>
     * should use a period(.) as the object delimeter. For example:<br>
     * <pre>
     * {@code
     *     {
     *         "response" : {
     *             "success": "true",
     *             "repo": {
     *                 "name": "repo1",
     *                 "href": "http://localhost:8080/geoserver/geogig/repos/repo1.json"
     *             }
     *         }
     *     }
     * }
     * </pre> To access the <b>success</b> value, the String "response.success" should be passed in.
     * <p>
     * To access the <b>name</b> value, the String "response.repo.name" should be passed in.
     *
     * @param jsonPath A String representing the value desired.
     *
     * @return A String representation of the value of the object denoted by the jsonPath.
     *
     * @throws JSONException
     */
    private String getStringFromJSONResponse(String jsonPath) throws JSONException {
        String response = context.getLastResponseText();
        JSONObject jsonResponse = new JSONObject(response);
        // find the JSON object
        String[] paths = jsonPath.split("\\.");
        JSONObject path = jsonResponse;
        for (int i = 0; i < paths.length - 1; ++i) {
            // drill down
            path = path.getJSONObject(paths[i]);
        }
        return path.getString(paths[paths.length - 1]);
    }

    private JSONArray getArrayFromJSONResponse(String jsonPath) throws JSONException {
        String response = context.getLastResponseText();
        JSONObject jsonResponse = new JSONObject(response);
        // find the JSON object
        String[] paths = jsonPath.split("\\.");
        JSONObject path = jsonResponse;
        for (int i = 0; i < paths.length - 1; ++i) {
            // drill down
            path = path.getJSONObject(paths[i]);
        }
        return path.getJSONArray(paths[paths.length - 1]);
    }

    private JSONObject getObjectFromJSONResponse(String jsonPath) throws JSONException {
        String response = context.getLastResponseText();
        JSONObject jsonResponse = new JSONObject(response);
        // find the JSON object
        String[] paths = jsonPath.split("\\.");
        JSONObject path = jsonResponse;
        for (int i = 0; i < paths.length; ++i) {
            // drill down
            path = path.getJSONObject(paths[i]);
        }
        return path;
    }

    @Then("^the json response \"([^\"]*)\" should contain \"([^\"]*)\" (\\d+) times$")
    public void checkJSONResponseContins(final String jsonArray, final String attribute,
            final int count) throws JSONException {
        JSONArray response = getArrayFromJSONResponse(jsonArray);
        assertEquals("JSON Response doesn't contain expected response correct number of times",
                count, response.length());
    }

    @Then("^the json response \"([^\"]*)\" should contain \"([^\"]*)\"$")
    public void checkJSONResponseContins(final String jsonArray, final String attribute) throws JSONException {
        JSONObject response = getObjectFromJSONResponse(jsonArray);
        assertTrue("JSON Response missing \"" + attribute + "\"", response.has(attribute));
    }

    @Then("^the json response \"([^\"]*)\" attribute \"([^\"]*)\" should each contain \"([^\"]*)\"$")
    public void checkJSONArrayContains(final String jsonArray, final String attribute,
            final String expected) throws JSONException {
        JSONArray array = getArrayFromJSONResponse(jsonArray);
        for (int i = 0; i < array.length(); ++i ) {
            JSONObject obj = array.getJSONObject(i);
            String actual = obj.getString(attribute);
            assertTrue("JSON response doesn't contain expected value, has: " + actual,
                    actual.contains(expected));
        }
    }

    @Then("^I save the first href link from \"([^\"]*)\" as \"([^\"]*)\"$")
    public void saveHrefLinkFromJSONResponse(final String jsonArray, final String href) throws JSONException {
        // get the first href link from the response
        JSONArray array = getArrayFromJSONResponse(jsonArray);
        JSONObject obj = array.getJSONObject(0);
        String link = obj.getString("href");
        // strip everything up to "repos" off the front of the href link
        String linkEnd = link.substring(link.indexOf("/repos"));
        // store the linkEnd
        context.setVariable(href, linkEnd);
    }

    @Then("^the json object \"([^\"]*)\" equals \"([^\"]*)\"$")
    public void checkJSONResponse(final String jsonPath, final String expected) throws JSONException {
        String pathValue = getStringFromJSONResponse(jsonPath);
        assertEquals("JSON Response doesn't match", expected, pathValue);
    }

    @Then("^the json object \"([^\"]*)\" ends with \"([^\"]*)\"$")
    public void checkJSONResponseEndsWith(final String jsonPath, final String expected)
            throws JSONException {
        String pathValue = getStringFromJSONResponse(jsonPath);
        assertTrue("JSON Response doesn't end with '" + expected + "'", pathValue.endsWith(expected));
    }

    @When("^I call \"([^\"]*)\" with the System Temp Directory as the parentDirectory$")
    public void callURLWithJSONPaylod(final String methodAndURL) throws JSONException {
        final int idx = methodAndURL.indexOf(' ');
        checkArgument(idx > 0, "No METHOD given in URL definition: '%s'", methodAndURL);
        final String httpMethod = methodAndURL.substring(0, idx);
        String resourceUri = methodAndURL.substring(idx + 1).trim();
        Method method = Method.valueOf(httpMethod);
        // build JSON payload
        JSONObject payload = new JSONObject();
        payload.put("parentDirectory", SYSTEM_TEMP_PATH);
        context.call(method, resourceUri, payload);
    }

    @When("^I call \"([^\"]*)\" with a URL encoded Form containing a parentDirectory parameter$")
    public void callURLWithFormPaylod(final String methodAndURL) throws JSONException {
        final int idx = methodAndURL.indexOf(' ');
        checkArgument(idx > 0, "No METHOD given in URL definition: '%s'", methodAndURL);
        final String httpMethod = methodAndURL.substring(0, idx);
        String resourceUri = methodAndURL.substring(idx + 1).trim();
        Method method = Method.valueOf(httpMethod);
        // build URL encoded Form
        Form form = new Form();
        form.add("parentDirectory", SYSTEM_TEMP_PATH);
        context.call(method, resourceUri, form);
    }

    @Then("^the parent directory of repository \"([^\"]*)\" equals System Temp directory$")
    public void checkRepositoryParent(final String repo) throws Exception {
        GeoGIG geogig = context.getRepo(repo);
        final Optional<URI> repoLocation = geogig.command(ResolveGeogigURI.class).call();
        assertTrue("Expected Repository location to be present", repoLocation.isPresent());
        URI repoURI = repoLocation.get();
        assertEquals("Unexpected URI scheme", "file", repoURI.getScheme());
        // parent of the repo is the directory that contains the ".geogig" directory.
        // the parent of the parent of the repo is the directory that the user specifies in the Init
        // request.
        String parentDir = new File(repoURI).getParentFile().getParentFile().getAbsolutePath();
        assertEquals("Unexpected parent directory", SYSTEM_TEMP_PATH, parentDir);
    }

    @Then("^the parent directory of repository \"([^\"]*)\" is NOT the System Temp directory$")
    public void checkRepositoryParent2(final String repo) throws Exception {
        GeoGIG geogig = context.getRepo(repo);
        final Optional<URI> repoLocation = geogig.command(ResolveGeogigURI.class).call();
        assertTrue("Expected Repository location to be present", repoLocation.isPresent());
        URI repoURI = repoLocation.get();
        assertEquals("Unexpected URI scheme", "file", repoURI.getScheme());
        // parent of the repo is the directory that contains the ".geogig" directory.
        // the parent of the parent of the repo is the directory that the user specifies in the Init
        // request.
        String parentDir = new File(repoURI).getParentFile().getParentFile().getAbsolutePath();
        assertNotEquals("Unexpected parent directory", SYSTEM_TEMP_PATH, parentDir);
    }
}
