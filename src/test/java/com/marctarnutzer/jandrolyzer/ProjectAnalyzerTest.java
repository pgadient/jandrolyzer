//
//  This file is part of jandrolyzer.
//
//  Created by Marc Tarnutzer on 29.01.2019.
//  Copyright © 2019 Marc Tarnutzer. All rights reserved.
//

package com.marctarnutzer.jandrolyzer;

import com.marctarnutzer.jandrolyzer.Models.JSONRoot;
import com.marctarnutzer.jandrolyzer.Models.Project;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.util.*;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ProjectAnalyzerTest {

    static List<Project> projects = new LinkedList<>();
    static HashMap<String, HashSet<String>> libraries;
    static Set<String> jsonModels = new HashSet<>();

    /*
     * Test Setup
     * -----------------------------------------------------------------------------------------------------------------
     */

    @BeforeClass
    public static void setup() {
        String projectPath = "/Users/marc/AndroidStudioProjects/JandrolyzerTestApplication";
        String librariesPath = "/Volumes/MTDocs/Libraries";

        libraries = Utils.getLibraries();

        try {
            ProjectAnalyzer projectAnalyzer = new ProjectAnalyzer(projectPath, libraries, projects, 1, librariesPath);
            projectAnalyzer.run();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        assertNotNull(projects);
        assertFalse(projects.isEmpty());

        for (JSONRoot jsonRoot : projects.get(0).jsonModels.values()) {
            jsonModels.add(jsonRoot.formatJSON());
        }
    }

    /*
     * OkHttpTesting Tests
     * -----------------------------------------------------------------------------------------------------------------
     */

    @Test
    public void okHttpURLExtractionTest1() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://okhttptestapifield.com"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapifield.com").endpoints.containsKey("api/path"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapifield.com").endpoints.get("api/path")
                .httpMethods.contains("DELETE"));
    }

    @Test
    public void okHttpURLExtractionTest2() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://okhttptestapi.com"));
    }

    @Test
    public void okHttpURLExtractionTest3() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://okhttptestapi2.com"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi2.com").endpoints.containsKey(""));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi2.com").endpoints.get("")
                .queries.containsKey("queryname1"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi2.com").endpoints.get("")
                .queries.containsKey("queryname2"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi2.com").endpoints.get("")
                .queries.containsKey("queryname3"));
    }

    @Test
    public void okHttpURLExtractionTest4() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://okhttptestapi3.com"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi3.com").endpoints.containsKey(""));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi3.com").endpoints.get("")
                .queries.containsKey("queryname1"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi3.com").endpoints.get("")
                .queries.containsKey("queryname2"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi3.com").endpoints.get("")
                .queries.containsKey("encqn1"));
    }

    @Test
    public void okHttpURLExtractionTest5() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://urlbuilderfieldapi.com"));
        assertTrue(projects.get(0).apiURLs.get("https://urlbuilderfieldapi.com").endpoints.containsKey("api/path1"));
    }

    @Test
    public void okHttpURLExtractionTest6() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://okhttptestapi4.com"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi4.com").endpoints.containsKey("api/path1_%2F_1/path2"));
    }

    @Test
    public void okHttpURLExtractionTest7() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://okhttptestapi5.com"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi5.com").endpoints.containsKey("api/pat%231/path2"));
    }

    @Test
    public void okHttpURLExtractionTest8() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://okhttptestapi6.com"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi6.com").endpoints.containsKey("api/path1/path2"));
    }

    @Test
    public void okHttpURLExtractionTest9() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://okhttptestapi7.com"));
        assertTrue(projects.get(0).apiURLs.get("http://okhttptestapi7.com").endpoints.containsKey("api/path1/path2"));
    }

    @Test
    public void okHttpURLExtractionTest10() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://1.1.1.1:23"));
        assertTrue(projects.get(0).apiURLs.get("http://1.1.1.1:23").endpoints.containsKey("api"));
        assertTrue(projects.get(0).apiURLs.get("http://1.1.1.1:23").endpoints.get("api")
                .fragments.contains("fragment/1"));
        assertTrue(projects.get(0).apiURLs.get("http://1.1.1.1:23").endpoints.get("api")
                .httpMethods.contains("POST"));
    }

    @Test
    public void okHttpURLExtractionTest11() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://2.2.2.2:23"));
        assertTrue(projects.get(0).apiURLs.get("http://2.2.2.2:23").endpoints.containsKey("api"));
        assertTrue(projects.get(0).apiURLs.get("http://2.2.2.2:23").endpoints.get("api")
                .httpMethods.contains("POST"));
    }

    @Test
    public void okHttpURLExtractionTest12() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://3.3.3.3:23"));
        assertTrue(projects.get(0).apiURLs.get("http://3.3.3.3:23").endpoints.containsKey("api"));
        assertTrue(projects.get(0).apiURLs.get("http://3.3.3.3:23").endpoints.get("api")
                .httpMethods.contains("POST"));
    }

    @Test
    public void okHttpURLExtractionTest13() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://4.4.4.4:23"));
        assertTrue(projects.get(0).apiURLs.get("http://4.4.4.4:23").endpoints.containsKey("api"));
        assertTrue(projects.get(0).apiURLs.get("http://4.4.4.4:23").endpoints.get("api")
                .httpMethods.contains("POST"));
    }

    @Test
    public void okHttpURLExtractionTest14() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://7.7.7.7:23"));
        assertTrue(projects.get(0).apiURLs.get("http://7.7.7.7:23").endpoints.containsKey("api"));
        assertTrue(projects.get(0).apiURLs.get("http://7.7.7.7:23").endpoints.get("api")
                .httpMethods.contains("POST"));
    }

    @Test
    public void okHttpURLExtractionTest15() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://5.5.5.5:23"));
        assertTrue(projects.get(0).apiURLs.get("http://5.5.5.5:23").endpoints.containsKey("api"));
        assertTrue(projects.get(0).apiURLs.get("http://5.5.5.5:23").endpoints.get("api")
                .httpMethods.contains("POST"));
    }

    @Test
    public void okHttpURLExtractionTest16() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://6.6.6.6:23"));
        assertTrue(projects.get(0).apiURLs.get("http://6.6.6.6:23").endpoints.containsKey("api"));
        assertTrue(projects.get(0).apiURLs.get("http://6.6.6.6:23").endpoints.get("api")
                .httpMethods.contains("POST"));
    }

    @Test
    public void okHttpURLExtractionTest17() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://8.8.8.8:23"));
        assertTrue(projects.get(0).apiURLs.get("http://8.8.8.8:23").endpoints.containsKey("api"));
        assertTrue(projects.get(0).apiURLs.get("http://8.8.8.8:23").endpoints.get("api")
                .httpMethods.contains("PATCH"));
    }

    @Test
    public void okHttpURLExtractionTest18() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://9.9.9.9:23"));
        assertTrue(projects.get(0).apiURLs.get("http://9.9.9.9:23").endpoints.containsKey("api"));
        assertTrue(projects.get(0).apiURLs.get("http://9.9.9.9:23").endpoints.get("api")
                .httpMethods.contains("PUT"));
    }

    /*
     * Retrofit Tests
     * -----------------------------------------------------------------------------------------------------------------
     */

    @Test
    public void retrofitTest1() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://retrofiturl.com"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.containsKey("api/loadUsers"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUsers")
                .queries.containsKey("position"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUsers")
                .queries.containsValue("<String>"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUsers")
                .queries.containsKey("order"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUsers")
                .queries.containsValue("<String>"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUsers")
                .httpMethods.contains("GET"));

        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.containsKey("api/loadNews"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadNews")
                .httpMethods.contains("GET"));

        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.containsKey("api/authUser"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/authUser")
                .httpMethods.contains("GET"));

        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.containsKey("api/loadUser/<Integer>"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUser/<Integer>")
                .httpMethods.contains("GET"));

        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.containsKey("api/headerRequest"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/headerRequest")
                .httpMethods.contains("GET"));

        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.containsKey("api/headerRequest2"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/headerRequest2")
                .httpMethods.contains("GET"));

        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.containsKey("api/loadUsers2"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUsers2")
                .queries.containsKey("position"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUsers2")
                .queries.containsValue("1234"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUsers2")
                .queries.containsKey("order"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUsers2")
                .queries.containsValue("rev"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/loadUsers2")
                .httpMethods.contains("GET"));

        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.containsKey("api/userHM"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/userHM")
                .httpMethods.contains("GET"));

        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.containsKey("api/createUser"));
        assertTrue(projects.get(0).apiURLs.get("http://retrofiturl.com").endpoints.get("api/createUser")
                .httpMethods.contains("POST"));
    }

    /*
     * String URL Tests
     * -----------------------------------------------------------------------------------------------------------------
     */

    @Test
    public void stringURLTest1() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://someapi.com"));
        assertTrue(projects.get(0).apiURLs.get("https://someapi.com").endpoints.containsKey("some/endpoint/path"));

        assertTrue(projects.get(0).apiURLs.get("https://someapi.com").endpoints
                .containsKey("some/endpoint/path/path2/path3/path4"));

        assertTrue(projects.get(0).apiURLs.get("https://someapi.com").endpoints
                .containsKey("some/endpoint/path/path2/path3"));

        assertTrue(projects.get(0).apiURLs.get("https://someapi.com").endpoints
                .containsKey("some/endpoint/path/path2"));

        assertTrue(projects.get(0).apiURLs.get("https://someapi.com").endpoints
                .containsKey("concatPath1"));

        assertTrue(projects.get(0).apiURLs.get("https://someapi.com").endpoints
                .containsKey("concatPath1/concatPath2"));

        assertTrue(projects.get(0).apiURLs.get("https://someapi.com").endpoints
                .containsKey("path_sb/path3"));
    }

    @Test
    public void stringURLTest2() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://someotherapi.com"));
        assertTrue(projects.get(0).apiURLs.get("http://someotherapi.com").endpoints.containsKey("path/path2"));

        assertTrue(projects.get(0).apiURLs.get("http://someotherapi.com").endpoints.containsKey("path"));
    }

    @Test
    public void stringURLTest3() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://thirdapi.ch"));
        assertTrue(projects.get(0).apiURLs.get("http://thirdapi.ch").endpoints.containsKey("pathname"));

        assertTrue(projects.get(0).apiURLs.get("http://thirdapi.ch").endpoints.containsKey("pathname/path2"));
    }

    @Test
    public void stringURLTest4() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://fourthapi.com"));
        assertTrue(projects.get(0).apiURLs.get("https://fourthapi.com").endpoints.containsKey("path1/path2/path3"));
    }

    @Test
    public void stringURLTest5() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://sixthapi.com"));
        assertTrue(projects.get(0).apiURLs.get("https://sixthapi.com").endpoints.containsKey("path1"));
    }

    @Test
    public void stringURLTest6() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://fifthapi.com"));
        assertTrue(projects.get(0).apiURLs.get("http://fifthapi.com").endpoints.containsKey("path1"));

        assertTrue(projects.get(0).apiURLs.get("http://fifthapi.com").endpoints.containsKey("path1_1/path2"));

        assertTrue(projects.get(0).apiURLs.get("http://fifthapi.com").endpoints.containsKey("path1_2"));

        assertTrue(projects.get(0).apiURLs.get("http://fifthapi.com").endpoints.containsKey("concatArgPath"));
    }

    @Test
    public void stringURLTest7() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://apiurl.com"));
        assertTrue(projects.get(0).apiURLs.get("https://apiurl.com").endpoints.containsKey("some/path"));

        assertTrue(projects.get(0).apiURLs.get("https://apiurl.com").endpoints.containsKey("some/path/path2"));
    }

    @Test
    public void stringURLTest8() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://apiurl2.com"));
        assertTrue(projects.get(0).apiURLs.get("https://apiurl2.com").endpoints.containsKey("some/path"));
    }

    @Test
    public void stringURLTest9() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://apiurl3.com"));
        assertTrue(projects.get(0).apiURLs.get("http://apiurl3.com").endpoints.containsKey("some/path"));
    }

    @Test
    public void stringURLTest10() {
        assertTrue(projects.get(0).apiURLs.containsKey("http://apiurl4.com:80"));
        assertTrue(projects.get(0).apiURLs.get("http://apiurl4.com:80").endpoints.containsKey("some/path"));
    }

    @Test
    public void stringURLTest11() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://baseurl.com"));
        assertTrue(projects.get(0).apiURLs.get("https://baseurl.com").endpoints.containsKey("path1"));
    }

    @Test
    public void stringURLTest12() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://staticbaseurl.com"));
        assertTrue(projects.get(0).apiURLs.get("https://staticbaseurl.com").endpoints.containsKey("path1"));

        assertTrue(projects.get(0).apiURLs.get("https://staticbaseurl.com").endpoints.containsKey("path2"));

        assertTrue(projects.get(0).apiURLs.get("https://staticbaseurl.com").endpoints.containsKey("static/path"));
    }

    @Test
    public void stringURLTest13() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://baseurl2.com"));
        assertTrue(projects.get(0).apiURLs.get("https://baseurl2.com").endpoints.containsKey("path1"));
    }

    @Test
    public void stringURLTest14() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://urlobjectapi.com"));
        assertTrue(projects.get(0).apiURLs.get("https://urlobjectapi.com").endpoints.containsKey("path1"));
    }

    @Test
    public void stringURLTest15() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://api.com"));
        assertTrue(projects.get(0).apiURLs.get("https://api.com").endpoints.containsKey("path2"));
    }

    @Test
    public void stringURLTest16() {
        assertTrue(projects.get(0).apiURLs.containsKey("https://staticsb.com"));
        assertTrue(projects.get(0).apiURLs.get("https://staticsb.com").endpoints.containsKey("path1"));
    }

    /*
     * String JSON extraction tests
     * -----------------------------------------------------------------------------------------------------------------
     */

    @Test
    public void stringJSONTest1() {
        assertTrue(jsonModels.contains("{\"jstestkey1\":\"testvalue1\"}"));
    }

    @Test
    public void stringJSONTest2() {
        assertTrue(jsonModels.contains("{\"jstestkey2\":\"testvalue2\"}"));
    }

    @Test
    public void stringJSONTest3() {
        assertTrue(jsonModels.contains("{\"jstestkey3\":\"testvalue3\"}"));
    }

    @Test
    public void stringJSONTest4() {
        assertTrue(jsonModels.contains("{\"jstestkey4\":\"testvalue4\"}"));
    }

    @Test
    public void stringJSONTest5() {
        assertTrue(jsonModels.contains("{\"jstestkey5\":\"testvalue5\"}"));
    }

    @Test
    public void stringJSONTest6() {
        assertTrue(jsonModels.contains("{\"jstestkey6\":\"testvalue6\"}"));
    }

    @Test
    public void stringJSONTest7() {
        assertTrue(jsonModels.contains("{\"jstestkey7\":\"testvalue7\"}"));
    }

    @Test
    public void stringJSONTest8() {
        assertTrue(jsonModels.contains("{\"jstestkey8\":8}"));
    }

    @Test
    public void stringJSONTest9() {
        assertTrue(jsonModels.contains("{\"jstestkey9\":true}"));
    }

    @Test
    public void stringJSONTest10() {
        assertTrue(jsonModels.contains("{\"jstestkey10\":10}"));
    }

    @Test
    public void stringJSONTest11() {
        assertTrue(jsonModels.contains("{\"jstestkey11\":\"<NUMBER_INT>\"}"));
    }

    @Test
    public void stringJSONTest12() {
        assertTrue(jsonModels.contains("{\"jstestkey12\":\"<STRING>\"}"));
    }

    /*
     * GSON JSON extraction tests
     * -----------------------------------------------------------------------------------------------------------------
     */

    @Test
    public void gsonJSONTest1() {
        assertTrue(jsonModels.contains("{\"is_vip_GSON\":\"<BOOLEAN>\",\"otherAddresses\":[{\"number\":\"<NUMBER_INT>\"},{\"name\":\"<STRING>\"}],\"address\":{\"name\":\"<STRING>\",\"number\":\"<NUMBER_INT>\"},\"nameGSON\":\"<STRING>\",\"id\":\"<NUMBER_INT>\"}"));
    }

    /*
     * Moshi JSON extraction tests
     * -----------------------------------------------------------------------------------------------------------------
     */

    @Test
    public void moshiJSONTest1() {
        assertTrue(jsonModels.contains("{\"name\":\"<STRING>\",\"is_vip\":\"<BOOLEAN>\",\"altAddresses\":[{\"number\":\"<NUMBER_INT>\"},{\"street\":\"<STRING>\"}],\"score\":\"<NUMBER_INT>\",\"address\":{\"street\":\"<STRING>\",\"number\":\"<NUMBER_INT>\"},\"userKind\":\"<STRING>\"}"));
    }

    /*
     * OkHttp JSON extraction tests
     * -----------------------------------------------------------------------------------------------------------------
     */

    @Test
    public void okHttpJSONTest1() {
        assertTrue(jsonModels.contains("{\"name\":\"test\"}"));
    }

    @Test
    public void okHttpJSONTest2() {
        assertTrue(jsonModels.contains("{\"name\":\"test2\"}"));
    }

    /*
     * Retrofit JSON extraction tests
     * -----------------------------------------------------------------------------------------------------------------
     */

    @Test
    public void retrofitJSONTest1() {
        assertTrue(jsonModels.contains("{\"name\":\"<STRING>\",\"address\":{\"street\":\"<STRING>\",\"number\":\"<NUMBER_INT>\"}}"));
    }

    /*
     * org.json JSON extraction tests
     * -----------------------------------------------------------------------------------------------------------------
     */

    @Test
    public void orgJSONTest1() {
        assertTrue(jsonModels.contains("{\"ojk1\":\"ojv1\",\"ojk2\":\"ojv2\",\"ojk3\":true}"));
    }

    @Test
    public void orgJSONTest2() {
        assertTrue(jsonModels.contains("{\"ojk7\":\"ojv7\",\"ojk9_1\":91,\"ojk8\":null,\"ojk9\":9}"));
    }

    @Test
    public void orgJSONTest3() {
        assertTrue(jsonModels.contains("{\"ojk4\":\"ojv4\",\"ojk5\":\"ojv5\",\"ojk6\":\"ojv6\"}"));
    }

    @Test
    public void orgJSONTest4() {
        assertTrue(jsonModels.contains("{\"ojk10\":\"ojv10\",\"ojk11\":\"ojv11\"}"));
    }

    @Test
    public void orgJSONTest5() {
        assertTrue(jsonModels.contains("{\"ojk12\":\"ojv12\",\"ojk13\":\"ojv13\"}"));
    }

    @Test
    public void orgJSONTest6() {
        assertTrue(jsonModels.contains("{\"oj16_1\":161,\"ojk16\":\"ojv16\",\"ojk15\":\"ojv15\",\"ojk14\":\"vv14\"}"));
    }

    @Test
    public void orgJSONTest7() {
        assertTrue(jsonModels.contains("{\"ojk19\":\"ojv19\",\"ojk18\":\"ojv18\",\"ojk17\":\"ojv17\"}"));
    }

    @Test
    public void orgJSONTest8() {
        assertTrue(jsonModels.contains("{\"ojk22\":\"ojv22\",\"ojk23\":[\"ojv23\"]}"));
    }

    @Test
    public void orgJSONTest9() {
        assertTrue(jsonModels.contains("{\"ojk10\":\"ojv10\",\"ojk25\":\"ojv25\"}"));
    }

    @Test
    public void orgJSONTest10() {
        assertTrue(jsonModels.contains("{\"ojk25\":\"ojv25\",\"ojk24\":\"ojv24\"}"));
    }

    // TODO add jsonarray tests
}
