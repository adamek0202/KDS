package cz.adam.kds;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;


/**
 * JavaFX KDS
 */
public class KDS extends Application {
    private VBox ordersBox = new VBox(10);
    private List<Order> orders = new ArrayList<>();
    private Gson gson = new Gson();

    public static void main(String[] args) {
        new Thread(KDS::startServer).start();
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        ordersBox.setStyle("-fx-background-color: black;");
        updateOrdersDisplay();

        StackPane root = new StackPane(ordersBox);
        Scene scene = new Scene(root, 800, 600);
        ordersBox.prefWidthProperty().bind(scene.widthProperty());
        ordersBox.prefHeightProperty().bind(scene.heightProperty());

        primaryStage.setTitle("KDS - Kitchen Display System");
        primaryStage.setScene(scene);
        primaryStage.setFullScreen(true);
        primaryStage.show();
    }

    private void loadOrdersFromJson(String jsonData) {
        orders.clear();
        JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
        JsonArray itemsArray = jsonObject.getAsJsonArray("items");
        List<Item> items = new ArrayList<>();

        for (var itemElement : itemsArray) {
            JsonObject itemObject = itemElement.getAsJsonObject();
            String type = itemObject.get("type").getAsString();
            String name = itemObject.get("name").getAsString();

            if ("composite".equals(type) && itemObject.has("components")) {
                JsonArray componentsArray = itemObject.getAsJsonArray("components");
                List<Component> components = new ArrayList<>();
                for (var componentElement : componentsArray) {
                    JsonObject componentObject = componentElement.getAsJsonObject();
                    int count = componentObject.get("count").getAsInt();
                    String componentName = componentObject.get("name").getAsString();
                    components.add(new Component(count, componentName));
                }
                items.add(new CompositeItem(name, components));
            } else {
                items.add(new NormalItem(name));
            }
        }
        orders.add(new Order(jsonObject.get("location").getAsString(), items));
        updateOrdersDisplay();
    }

    private void updateOrdersDisplay() {
        ordersBox.getChildren().clear();
        for (Order order : orders) {
            VBox orderBox = new VBox();
            orderBox.setStyle("-fx-border-color: white; -fx-padding: 20; -fx-background-color: gray;");
            StringBuilder orderText = new StringBuilder(order.location + "\n");
            for (Item item : order.items) {
                if (item instanceof CompositeItem) {
                    CompositeItem composite = (CompositeItem) item;
                    orderText.append(composite.name).append("\n");
                    for (Component component : composite.components) {
                        orderText.append("  - ").append(component.count).append("x ").append(component.name).append("\n");
                    }
                } else if (item instanceof NormalItem) {
                    NormalItem normal = (NormalItem) item;
                    orderText.append("1x ").append(normal.name).append("\n");
                }
            }
            Label orderLabel = new Label(orderText.toString());
            orderLabel.setFont(new Font(24));
            orderLabel.setTextFill(Color.WHITE);
            orderBox.getChildren().add(orderLabel);
            orderBox.prefWidthProperty().bind(ordersBox.widthProperty());

            orderBox.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    orders.remove(order);
                    updateOrdersDisplay();
                }
            });

            ordersBox.getChildren().add(orderBox);
        }
    }

    private static void startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(9000), 0);
            server.createContext("/order", new OrderHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("Server started on port 9000");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                KDS app = new KDS();
                app.loadOrdersFromJson(requestBody);
                exchange.sendResponseHeaders(200, 0);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
            exchange.close();
        }
    }

    private static class Order {
        String location;
        List<Item> items;
        Order(String location, List<Item> items) {
            this.location = location;
            this.items = items;
        }
    }

    private interface Item {}

    private static class NormalItem implements Item {
        String name;
        NormalItem(String name) {
            this.name = name;
        }
    }

    private static class CompositeItem implements Item {
        String name;
        List<Component> components;
        CompositeItem(String name, List<Component> components) {
            this.name = name;
            this.components = components;
        }
    }

    private static class Component {
        int count;
        String name;
        Component(int count, String name) {
            this.count = count;
            this.name = name;
        }
    }
}

