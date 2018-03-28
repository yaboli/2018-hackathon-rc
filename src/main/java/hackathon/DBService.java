package hackathon;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.bson.Document;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class DBService {

    private final String keywordsUrl = "https://eastus.api.cognitive.microsoft.com/text/analytics/v2.0/keyPhrases";
    private final String subscriptionKey = "b14971ee161d4a1496807d54936874ca";

    private final String connectionString = "mongodb://concordiadb:NbMIyi98erqZlnvbPrhFP1Cnu3Aglx6juyQV9z03Qvi1QrQ3Dt" +
            "CFtuyn6wla1FyAnzSlXGJUT2nnlVfde4mj0g==@concordiadb.documents.azure.com:10255/?ssl=true&replicaSet=globaldb";
    private MongoClientURI uri;
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;
    private String databaseId = "TestDB";
    private String collectionId = "TestColl";
//     private String collectionId = "TestColl2";

    private String radioCanadaUri = null;
    private int searchRangeStart = 1000001;
    private int searchRangeEnd = 1000010;


    @PostConstruct
    private void init() {

        uri = new MongoClientURI(connectionString);
        mongoClient = new MongoClient(uri);
        database = mongoClient.getDatabase(databaseId);
        collection = database.getCollection(collectionId);
    }

    public ArrayList<String> getData() {

        // Grab all data and send it to invoker
        ArrayList<String> documents = new ArrayList<String>();
        MongoCursor<Document> cursor = collection.find().iterator();
        try {
            while (cursor.hasNext()) {
                documents.add(cursor.next().toJson());
            }
        } finally {
            cursor.close();
        }

        return documents;
    }

    public void saveDataToDB() throws IOException {

        List<Document> docs = new ArrayList<Document>();

        for (int i = searchRangeStart; i <= searchRangeEnd; i++) {

            radioCanadaUri = "https://services.radio-canada.ca/hackathon/neuro/v1/news-stories/"
                    + Integer.toString(i)
                    + "?client_key=bf9ac6d8-9ad8-4124-a63c-7b7bdf22a2ee";

            // Get Radio Canada content
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(radioCanadaUri);
            CloseableHttpResponse response = httpClient.execute(httpGet);
            HttpEntity responseEntity = response.getEntity();
            String responseString = EntityUtils.toString(responseEntity);

            // Parse received json object
            JSONObject responseJson = new JSONObject(responseString);

            JSONObject canonicalWebLink;
            String href = "No available link";
            if (responseJson.has("canonicalWebLink")) {
                canonicalWebLink = (JSONObject) responseJson.get("canonicalWebLink");
                href = (String) canonicalWebLink.get("href");
            }

            String title = "No title";
            if (responseJson.has("title")) title = (String) responseJson.get("title");

            String summaryText = "résumé vide";
            if (responseJson.has("summary")) {
                String summaryHtml = (String) responseJson.get("summary");
                summaryText = Jsoup.parse(summaryHtml).select("p").text();
            }

            String bodyText = "corps vide";
            if (responseJson.has("body")) {
                JSONObject body = (JSONObject) responseJson.get("body");
                String bodyHtml = (String) body.get("html");
                bodyHtml = bodyHtml.replace("\n", "");
                if (!Jsoup.parse(bodyHtml).text().equals("")) bodyText = Jsoup.parse(bodyHtml).text();
            }

            // Invoke Azure Text Analytics API
            JSONObject azureResponse = getKeyPhrases(summaryText, bodyText);
            JSONArray documents = (JSONArray) azureResponse.get("documents");
            JSONArray errors = (JSONArray) azureResponse.get("errors");

            // Get key phrases of summary
            JSONObject item1 = (JSONObject) documents.get(0);
            JSONArray arr1 = (JSONArray) item1.get("keyPhrases");
            ArrayList<String> summaryKeyPhrases = new ArrayList<String>();
            for (int j = 0; j < arr1.length(); j++) {
                summaryKeyPhrases.add(arr1.getString(j));
            }

            JSONObject item2 = null;
            ArrayList<String> bodyKeyPhrases = new ArrayList<String>();
            if (documents.length() > 1) {
                // Get key phrases of body
                item2 = (JSONObject) documents.get(1);
                JSONArray arr2 = (JSONArray) item2.get("keyPhrases");
                bodyKeyPhrases = new ArrayList<String>();
                for (int k = 0; k < arr2.length(); k++) {
                    bodyKeyPhrases.add(arr2.getString(k));
                }
            }

            // Save news item into MongoDB
            Document doc = new Document("title", title).
                    append("href", href).
                    append("summary", summaryKeyPhrases).
                    append("body", bodyKeyPhrases).
                    append("errors", errors.toString());

            docs.add(doc);
        }

        collection.insertMany(docs);
    }

    public JSONObject getKeyPhrases(String summary, String body) throws IOException {

        // Construct text analytics api request
        String language = "fr";
        String id1 = "summary";
        String id2 = "body";
        JSONObject content = new JSONObject();
        JSONArray documents = new JSONArray();
        JSONObject document1 = new JSONObject();
        JSONObject document2 = new JSONObject();
        document1.put("language", language);
        document1.put("id", id1);
        document1.put("text", summary);
        document2.put("language", language);
        document2.put("id", id2);
        document2.put("text", body);
        documents.put(document1);
        documents.put(document2);
        content.put("documents", documents);

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost(keywordsUrl);
        httppost.addHeader("Ocp-Apim-Subscription-Key", subscriptionKey);

        HttpEntity entity = new StringEntity(content.toString(), ContentType.APPLICATION_JSON);
        httppost.setEntity(entity);

        CloseableHttpResponse response = httpClient.execute(httppost);
        HttpEntity responseEntity = response.getEntity();
        String responseString = EntityUtils.toString(responseEntity);
        return new JSONObject(responseString);
    }

    public void deleteAllData() {
        collection.deleteMany(Filters.exists("_id"));
    }

    @PreDestroy
    public void destroy() {
        mongoClient.close();
    }
}
