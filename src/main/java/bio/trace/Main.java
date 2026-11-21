package bio.trace;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) throws IOException {
        // 1. Instancia as nossas ferramentas
        JiraClient jiraClient = new JiraClient();
        Gson gson = new Gson();

        // 2. Abre a porta 8080 no seu PC para ouvir requisições
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // 3. Cria o endereço (rota) que o Web vai chamar
        server.createContext("/api/dashboard/chamados", exchange -> {

            // Libera o acesso caso o navegador/Front-end faça bloqueio de segurança (CORS)
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            // O navegador manda um "OPTIONS" antes do POST para testar se a porta tá aberta
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Garante que o Web mandou um POST
            if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    // Passo A: Lê o texto JSON que veio do Web (ex: {"responsavel": "Amanda"})
                    String corpoRequisicao = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    System.out.println("1. Web pediu: " + corpoRequisicao);

                    // Passo B: Usa o Gson para tirar só o nome de dentro do JSON
                    JsonObject jsonRecebido = gson.fromJson(corpoRequisicao, JsonObject.class);
                    String responsavel = jsonRecebido.get("responsavel").getAsString();

                    // Passo C: Manda o JiraClient ir lá no Jira buscar e mastigar tudo
                    System.out.println("2. Buscando chamados de '" + responsavel + "' no Jira...");
                    String jsonProntoProWeb = jiraClient.buscarChamadosPorResponsavel(responsavel);

                    // Passo D: Devolve o JSON mastigado de volta para o Web com status 200 (Sucesso)
                    System.out.println("3. Devolvendo dados prontos para o Web!");
                    byte[] respostaBytes = jsonProntoProWeb.getBytes(StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(200, respostaBytes.length);

                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(respostaBytes);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    // Se der algum erro no meio do caminho, avisa o Web com status 500
                    String jsonErro = "{\"erro\": \"" + e.getMessage() + "\"}";
                    byte[] erroBytes = jsonErro.getBytes(StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                    exchange.sendResponseHeaders(500, erroBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(erroBytes);
                    }
                }
            } else {
                // Se tentarem acessar via GET no navegador direto, avisa que só aceita POST
                exchange.sendResponseHeaders(405, -1);
            }
        });

        // 4. Liga o servidor
        server.start();
        System.out.println("Servidor rodando! Aguardando o Web em: http://localhost:8080/api/dashboard/chamados");
    }
}