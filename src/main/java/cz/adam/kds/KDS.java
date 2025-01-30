package cz.adam.kds;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class KDS {
    private static final List<Order> orders = new ArrayList<>();
    private static final Gson gson = new Gson();
    private static JPanel ordersPanel;
    private static JFrame frame;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(KDS::createAndShowGUI);
        new Thread(KDS::startServer).start();
    }

    private static void createAndShowGUI() {
        frame = new JFrame("KDS - Kitchen Display System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

        ordersPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JScrollPane scrollPane = new JScrollPane(ordersPanel);

        frame.add(scrollPane);
        frame.setVisible(true);
    }

    private static void loadOrdersFromJson(String jsonData) {
        JsonObject jsonObject = gson.fromJson(jsonData, JsonObject.class);
        JsonArray itemsArray = jsonObject.getAsJsonArray("items");
        List<Item> items = new ArrayList<>();

        for (var itemElement : itemsArray) {
            JsonObject itemObject = itemElement.getAsJsonObject();
            String type = itemObject.get("type").getAsString();
            String name = itemObject.get("name").getAsString();
            List<Item> components = new ArrayList<>();

            if ("composite".equals(type) && itemObject.has("components")) {
                JsonArray componentsArray = itemObject.getAsJsonArray("components");
                for (var compElement : componentsArray) {
                    JsonObject compObject = compElement.getAsJsonObject();
                    int count = compObject.get("count").getAsInt();
                    String compName = compObject.get("name").getAsString();
                    components.add(new Item(compName, count, null));
                }
            }

            items.add(new Item(name, 1, components));
        }

        orders.add(new Order(jsonObject.get("location").getAsString(), items));
        updateOrdersDisplay();
    }

    private static void updateOrdersDisplay() {
        SwingUtilities.invokeLater(() -> {
            ordersPanel.removeAll();
            for (Order order : orders) {
                JPanel orderPanel = new JPanel();
                orderPanel.setLayout(new BoxLayout(orderPanel, BoxLayout.Y_AXIS));
                orderPanel.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
                orderPanel.setBackground(Color.LIGHT_GRAY);
                orderPanel.setPreferredSize(new Dimension(200, 150));

                StringBuilder orderText = new StringBuilder("<html><b>" + order.location + "</b><br>");
                for (Item item : order.items) {
                    orderText.append(item.count).append("x ").append(item.name).append("<br>");
                    if (item.components != null) {
                        for (Item comp : item.components) {
                            orderText.append("- ").append(comp.count).append("x ").append(comp.name).append("<br>");
                        }
                    }
                }
                orderText.append("</html>");

                JLabel orderLabel = new JLabel(orderText.toString());
                orderPanel.add(orderLabel);

                orderPanel.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (e.getClickCount() == 2) {
                            orders.remove(order);
                            updateOrdersDisplay();
                        }
                    }
                });

                ordersPanel.add(orderPanel);
            }
            ordersPanel.revalidate();
            ordersPanel.repaint();
        });
    }

    private static void startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/order", new OrderHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("Server started on port 8080");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class OrderHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = new String(exchange.getRequestBody().readAllBytes());
                loadOrdersFromJson(requestBody);

                String response = "{\"status\": \"OK\"}";
                exchange.sendResponseHeaders(200, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
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

    private static class Item {
        String name;
        int count;
        List<Item> components;
        Item(String name, int count, List<Item> components) {
            this.name = name;
            this.count = count;
            this.components = components;
        }
    }
}
