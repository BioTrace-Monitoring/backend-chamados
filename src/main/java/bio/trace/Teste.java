package bio.trace;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Teste {



        public static void main(String[] args) throws IOException {

            String dominio = System.getenv("JIRA_DOMINIO");
            String email = System.getenv("JIRA_EMAIL");
            String token = System.getenv("JIRA_API_TOKEN");

            System.out.println("DOMINIO: " + dominio);
            System.out.println("EMAIL: " + email);
            System.out.println("TOKEN CONFIGURADO: " + (token != null && !token.isBlank()));

            HttpServer servidor = HttpServer.create(
                    new InetSocketAddress(8080), 0
            );

            servidor.createContext("/api/dashboard", exchange -> {

                try {
                    JiraClient jiraClient = new JiraClient();


                    String id = jiraClient.buscarAccountIdPorNome("Bruno Rafael");
                    System.out.println("ID do Bruno: " + id);
                    String json = jiraClient.buscarChamadosPorResponsavel(
                            "Bruno Rafael"
                    );

                    byte[] resposta = json.getBytes(StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().set(
                            "Content-Type", "application/json; charset=UTF-8"
                    );

                    exchange.sendResponseHeaders(200, resposta.length);

                    exchange.getResponseBody().write(resposta);
                    exchange.getResponseBody().close();

                } catch (Exception e) {

                    String erro = "{\"erro\":\"Falha ao buscar dados do Jira\"}";

                    byte[] resposta = erro.getBytes(StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().set(
                            "Content-Type", "application/json; charset=UTF-8"
                    );

                    exchange.sendResponseHeaders(500, resposta.length);
                    exchange.getResponseBody().write(resposta);
                    exchange.getResponseBody().close();

                    e.printStackTrace();
                }
            });

            servidor.start();

            System.out.println("Servidor iniciado!");
            System.out.println("Acesse: http://localhost:8080/api/dashboard");
        }
}

