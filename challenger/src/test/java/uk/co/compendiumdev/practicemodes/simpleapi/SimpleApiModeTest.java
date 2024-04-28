package uk.co.compendiumdev.practicemodes.simpleapi;

import com.google.gson.Gson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import uk.co.compendiumdev.challenger.http.httpclient.HttpMessageSender;
import uk.co.compendiumdev.challenger.http.httpclient.HttpResponseDetails;
import uk.co.compendiumdev.sparkstart.Environment;

import java.util.*;
import java.util.stream.Stream;

/*
    The Simple API is wired up using the default thingifier so we are mainly confirming format,
    specific API Config, and wiring
 */
public class SimpleApiModeTest {

    private static HttpMessageSender http;


    @BeforeAll
    static void createHttp(){
        // this uses the Environment to startup the spark app to
        // issue http tests and test the routing in spark
        http = new HttpMessageSender(Environment.getBaseUri());
    }

    static Stream<Arguments> simpleRoutingStatus(){
        List<Arguments> args = new ArrayList<>();


        args.add(Arguments.of(200, "get", "/simpleapi/items"));
        args.add(Arguments.of(200, "head", "/simpleapi/items"));
        args.add(Arguments.of(204, "options", "/simpleapi/items"));
        args.add(Arguments.of(405, "patch", "/simpleapi/items"));
        args.add(Arguments.of(405, "trace", "/simpleapi/items"));
        args.add(Arguments.of(405, "delete", "/simpleapi/items"));
        args.add(Arguments.of(405, "put", "/simpleapi/items"));
        args.add(Arguments.of(400, "post", "/simpleapi/items"));


        return args.stream();
    }

    @ParameterizedTest(name = "simple status routing expected {0} for {1} {2}")
    @MethodSource("simpleRoutingStatus")
    void simpleRoutingTest(int statusCode, String verb, String url){
        final HttpResponseDetails response =
                http.send(url, verb);

        Assertions.assertEquals(statusCode, response.statusCode);
    }


    @Test
    public void canPostItemAsXmlAndAcceptJson() {

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/xml");
        headers.put("Accept", "application/json");

        // Minimum payload
        final HttpResponseDetails response =
                http.send("/simpleapi/items", "POST", headers,
                        """
                        <item>
                            <price>1.64</price>
                            <isbn13>128-6-32-856404-0</isbn13>
                            <type>cd</type>
                        </item>
                        """.stripIndent());

        Assertions.assertEquals(201, response.statusCode);
        Assertions.assertEquals("application/json", response.getHeader("content-type"));

        Item item = new Gson().fromJson(response.body, Item.class);
        Assertions.assertEquals(1.64f, item.price);
        Assertions.assertEquals(0, item.numberinstock);
        Assertions.assertEquals("128-6-32-856404-0", item.isbn13);
        Assertions.assertEquals("cd", item.type);
    }

    @Test
    public void canPostItemAsJsonAndAcceptJson() {

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        // full valid payload
        final HttpResponseDetails response =
                http.send("/simpleapi/items", "POST", headers,
                        """
                            {
                            "price":2.00,
                            "numberinstock":2,
                            "isbn13": "1234567890123",
                            "type":book
                        }
                        """.stripIndent());

        Assertions.assertEquals(201, response.statusCode);
        Assertions.assertEquals("application/json", response.getHeader("content-type"));

        Item item = new Gson().fromJson(response.body, Item.class);
        Assertions.assertEquals(2f, item.price);
        Assertions.assertEquals(2, item.numberinstock);
        Assertions.assertEquals("1234567890123", item.isbn13);
        Assertions.assertEquals("book", item.type);
    }


    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "-"
    })
    public void canNotPostCreateItemsWithDuplicateISBN(String dupeSynonymReplace) {

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        Random random = new Random();
        String aRandomIsbn = randomIsbn(random);

        // full valid payload
        final HttpResponseDetails response =
                http.send("/simpleapi/items", "POST", headers,
                        """
                            {
                            "price":2.00,
                            "numberinstock":2,
                            "isbn13": "%s",
                            "type":book
                        }
                        """.formatted(aRandomIsbn).stripIndent());

        final HttpResponseDetails duplicateIsbnResponse =
                http.send("/simpleapi/items", "POST", headers,
                        """
                            {
                            "price":2.00,
                            "numberinstock":2,
                            "isbn13": "%s",
                            "type":book
                        }
                        """.formatted(aRandomIsbn.replace("-",dupeSynonymReplace)).stripIndent());

        Assertions.assertEquals(201, response.statusCode);
        Assertions.assertEquals("application/json", response.getHeader("content-type"));

        Assertions.assertEquals(400, duplicateIsbnResponse.statusCode);
        Assertions.assertTrue(duplicateIsbnResponse.body.contains("Field isbn13 Value is not unique"), "did not expect " + duplicateIsbnResponse.body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT"})
    public void canNotAmendItemsToHaveDuplicateISBN(String verbPostPut) {

        Random random = new Random();

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        Item anItem = new Item();
        anItem.type="book";
        anItem.price=2.00F;
        anItem.isbn13 = randomIsbn(random);

        Item anItemToAmend = new Item();
        anItemToAmend.type="book";
        anItemToAmend.price=3.00F;
        anItemToAmend.isbn13 = randomIsbn(random);

        apiCreateItem(anItem);
        Item itemToAmend = apiCreateItem(anItemToAmend);

        final HttpResponseDetails amendResponse =
                http.send("/simpleapi/items/"+itemToAmend.id, verbPostPut, headers,
                        """
                            {
                            "price":2.00,
                            "numberinstock":2,
                            "isbn13": "%s",
                            "type":book
                        }
                        """.formatted(anItem.isbn13).stripIndent());


        Assertions.assertEquals(400, amendResponse.statusCode, "should not be able to amend item to " + amendResponse.body);
        Assertions.assertTrue(amendResponse.body.contains("Field isbn13 Value is not unique"), "did not expect " + amendResponse.body);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "-"
    })
    public void canNotAmendItemsToHaveDuplicateISBNBasedOnUniqueComparison(String dupeSynonymReplace) {

        Random random = new Random();

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        Item anItem = new Item();
        anItem.type="book";
        anItem.price=2.00F;
        anItem.isbn13 = randomIsbn(random);

        Item anItemToAmend = new Item();
        anItemToAmend.type="book";
        anItemToAmend.price=3.00F;
        anItemToAmend.isbn13 = randomIsbn(random);

        apiCreateItem(anItem);
        Item itemToAmend = apiCreateItem(anItemToAmend);

        final HttpResponseDetails amendResponse =
                http.send("/simpleapi/items/"+itemToAmend.id, "POST", headers,
                        """
                            {
                            "price":2.00,
                            "numberinstock":2,
                            "isbn13": "%s",
                            "type":book
                        }
                        """.formatted(anItem.isbn13.replace("-",dupeSynonymReplace)).stripIndent());


        Assertions.assertEquals(400, amendResponse.statusCode, "should not be able to amend item to " + amendResponse.body);
        Assertions.assertTrue(amendResponse.body.contains("Field isbn13 Value is not unique"), "did not expect " + amendResponse.body);
    }

    @Test
    public void cannotPostItemWithId() {

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        // full valid payload
        final HttpResponseDetails response =
                http.send("/simpleapi/items", "POST", headers,
                        """
                            {
                            "id" : 1,
                            "price":2.00,
                            "numberinstock":2,
                            "isbn13": "1234567890123",
                            "type":book
                        }
                        """.stripIndent());

        Assertions.assertEquals(400, response.statusCode);
        Assertions.assertEquals("application/json", response.getHeader("content-type"));

        ErrorMessagesResponse error = new Gson().fromJson(response.body, ErrorMessagesResponse.class);
        Assertions.assertEquals(1, error.errorMessages.size());
        Assertions.assertTrue(error.errorMessages.get(0).contains("Not allowed to create with id"));
    }


    @Test
    public void canGetItemsAsJson() {

        Items items = apiGetItems();
        Assertions.assertFalse(items.items.isEmpty());
    }

    @Test
    public void canCreateAnItemAndThenGetIt(){

        Item itemToCreate = new Item();
        itemToCreate.isbn13="1111111111111";
        itemToCreate.price=1.23f;
        itemToCreate.type="book";

        Item createdItem = apiCreateItem(itemToCreate);

        Item retrievedItem = apiGetItem(createdItem.id);

        Assertions.assertEquals(createdItem.id, retrievedItem.id);
        Assertions.assertEquals(itemToCreate.isbn13, retrievedItem.isbn13);
        Assertions.assertEquals(itemToCreate.price, retrievedItem.price);
        Assertions.assertEquals(itemToCreate.type, retrievedItem.type);
    }

    @Test
    public void canCreateAnItemAndThenDeleteIt(){

        Item itemToCreate = new Item();
        itemToCreate.isbn13="9111111111111";
        itemToCreate.price=1.23f;
        itemToCreate.type="book";

        Item createdItem = apiCreateItem(itemToCreate);

        HttpResponseDetails response = apiDeleteItem(createdItem.id);

        Assertions.assertEquals(200, response.statusCode);
        Assertions.assertEquals(404, apiGetItemResponse(createdItem.id).statusCode);
    }


    @Test
    public void canOnlyCreateAMaxOf100Items(){

        Items items = apiGetItems();

        int numberToCreate = 100- items.items.size();

        Item itemToCreate = new Item();
        itemToCreate.isbn13= "0001234567890";
        itemToCreate.price=1.23f;
        itemToCreate.type="book";

        for(int itemx=0; itemx<numberToCreate; itemx++){
            itemToCreate.isbn13= String.format("%03d", itemx) + "1234567890";
            apiCreateItem(itemToCreate);
        }

        itemToCreate.isbn13= "101" + "1234567890";
        HttpResponseDetails finalResponse = apiCreateItemResponse(itemToCreate);
        Assertions.assertEquals(400, finalResponse.statusCode);

        ErrorMessagesResponse errorMessage = new Gson().fromJson(finalResponse.body, ErrorMessagesResponse.class);
        Assertions.assertEquals(errorMessage.errorMessages.get(0), "ERROR: Cannot add instance, maximum limit of 100 reached");
    }

    @Test
    public void canNeverDeleteItemsToEmpty(){

        Items items = apiGetItems();

        for(Item item : items.items){
            apiDeleteItem(item.id);
        }

        // and yet there are some left
        Items moreItems = apiGetItems();

        // the data populator creates 8 items
        Assertions.assertEquals(8, moreItems.items.size());
    }

    @Test
    public void dataRepopulationHappensWhen3Left(){

        Items items = apiGetItems();

        int itemsLeft = items.items.size();

        for(Item item : items.items){
            apiDeleteItem(item.id);
            itemsLeft--;
            if(itemsLeft==5) break;
        }

        // population kicks in when less than 5
        Items itemsPrePopulation = apiGetItems();
        Assertions.assertEquals(5, itemsPrePopulation.items.size());

        // delete one more for repopulation
        apiDeleteItem(itemsPrePopulation.items.get(0).id);

        // repopulation should kick in
        // so check there are some left
        Items moreItems = apiGetItems();

        // the data populator creates 8 items
        Assertions.assertEquals(4+8, moreItems.items.size());
    }




/*
    All the api abstraction classes to support the simple HTTP Tests
 */

    private HttpResponseDetails apiGetItemResponse(Integer id) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");

        return http.send("/simpleapi/items/" + id, "GET", headers, "");
    }

    private Item apiGetItem(Integer id) {
        final HttpResponseDetails response = apiGetItemResponse(id);

        Assertions.assertEquals(200, response.statusCode);
        Assertions.assertEquals("application/json", response.getHeader("content-type"));

        // thingifier is configurable and can send a single item back on a GET /things/:id
        return new Gson().fromJson(response.body, Item.class);
    }

    private HttpResponseDetails apiCreateItemResponse(Item itemToCreate) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        return http.send("/simpleapi/items", "POST", headers,
                        new Gson().toJson(itemToCreate));
    }

    private Item apiCreateItem(Item itemToCreate) {

        final HttpResponseDetails response = apiCreateItemResponse(itemToCreate);

        Assertions.assertEquals(201, response.statusCode);
        Assertions.assertEquals("application/json", response.getHeader("content-type"));

        return new Gson().fromJson(response.body, Item.class);
    }

    private HttpResponseDetails apiDeleteItem(Integer id) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "application/json");

        return http.send("/simpleapi/items/" + id, "DELETE", headers, "");
    }

    private Items apiGetItems(){
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "application/json");

        final HttpResponseDetails response =
                http.send("/simpleapi/items", "GET", headers,
                        """
                        """.stripIndent());

        Assertions.assertEquals(200, response.statusCode);
        Assertions.assertEquals("application/json", response.getHeader("content-type"));

        return new Gson().fromJson(response.body, Items.class);
    }

    static class Item{

        Integer id;
        Float price;
        Integer numberinstock;
        String isbn13;
        String type;
    }

    static class Items{
        List<Item> items;
    }

    static class ErrorMessagesResponse{
        List<String> errorMessages;
    }

    private String randomIsbn(Random random){

        String isbn13 = "xxx-x-xx-xxxxxx-x";

        while(isbn13.contains("x")){
            isbn13 = isbn13.replaceFirst("x", String.valueOf(random.nextInt(9)));
        }

        return isbn13;
    }
}
