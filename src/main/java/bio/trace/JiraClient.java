package bio.trace;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class JiraClient {

    // 1. SUAS CREDENCIAIS DO JIRA (Preencha com os dados do seu grupo)
    private static final String JIRA_DOMINIO = System.getenv("JIRA_DOMINIO").trim();
    private static final String JIRA_EMAIL = System.getenv("JIRA_EMAIL").trim();
    private static final String JIRA_API_TOKEN = System.getenv("JIRA_API_TOKEN").trim();
    // Ferramentas que o Java vai usar para navegar na internet e ler JSON
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public String buscarAccountIdPorNome(String nome)
            throws IOException, InterruptedException {

        String query = URLEncoder.encode(nome, StandardCharsets.UTF_8);

        String url = JIRA_DOMINIO
                + "/rest/api/3/user/search?query=" + query;

        String credenciais = JIRA_EMAIL + ":" + JIRA_API_TOKEN;

        String authBase64 = Base64.getEncoder()
                .encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + authBase64)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException("Erro ao buscar usuário: "
                    + response.body());
        }

        JsonArray usuarios = gson.fromJson(response.body(), JsonArray.class);

        for (JsonElement elemento : usuarios) {
            JsonObject usuario = elemento.getAsJsonObject();

            String nomeEncontrado = usuario.get("displayName").getAsString();

            if (nomeEncontrado.equalsIgnoreCase(nome)) {
                return usuario.get("accountId").getAsString();
            }
        }

        throw new RuntimeException("Usuário não encontrado: " + nome);
    }

    // Método principal dessa classe: recebe um nome (ex: "Amanda") e devolve o JSON pronto pro Web
    public String buscarChamadosPorResponsavel(String responsavel) throws IOException, InterruptedException {

        // Passo A: Monta a pergunta na linguagem do Jira (JQL)
        String accountId = buscarAccountIdPorNome(responsavel);

        String jql = "assignee = \"" + accountId
                + "\" ORDER BY created DESC";
        String jqlCodificada = URLEncoder.encode(jql, StandardCharsets.UTF_8);
        String url = JIRA_DOMINIO + "/rest/api/3/search/jql?jql=" + jqlCodificada
                + "&fields=summary,status,priority,created,resolutiondate";

        // Passo B: Prepara a senha no formato que o Jira exige (email:token em Base64)
        String credenciais = JIRA_EMAIL + ":" + JIRA_API_TOKEN;
        String authBase64 = Base64.getEncoder().encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));

        System.out.println("URL=" + url);
        System.out.println("EMAIL=[" + JIRA_EMAIL + "] len=" + JIRA_EMAIL.length());
        System.out.println("TOKEN len=" + JIRA_API_TOKEN.length()
                + " inicio=" + JIRA_API_TOKEN.substring(0, 4)
                + " fim=" + JIRA_API_TOKEN.substring(JIRA_API_TOKEN.length() - 4));

        HttpRequest quemSou = HttpRequest.newBuilder()
                .uri(URI.create(JIRA_DOMINIO + "/rest/api/3/myself"))
                .header("Authorization", "Basic " + authBase64)
                .header("Accept", "application/json")
                .GET().build();
        HttpResponse<String> rq = httpClient.send(quemSou, HttpResponse.BodyHandlers.ofString());
        System.out.println("MYSELF HTTP " + rq.statusCode() + ": "
                + rq.body().substring(0, Math.min(300, rq.body().length())));
        // Passo C: Faz o pedido GET para o Jira (igual um navegador entrando no link)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + authBase64)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("STATUS HTTP: " + response.statusCode());
        System.out.println("RESPOSTA DO JIRA:");
        System.out.println(response.body());

        if (response.statusCode() != 200) {
            throw new RuntimeException("O Jira recusou o pedido (HTTP " + response.statusCode() + "): " + response.body());
        }

        // Passo D: Pega o JSON gigante do Jira e extrai só a lista de chamados ("issues")
        JsonObject respostaBruta = gson.fromJson(response.body(), JsonObject.class);
        JsonArray listaBruta = respostaBruta.getAsJsonArray("issues");

        // Passo E: Cria uma lista nova e limpa para devolver pro Web
        JsonArray chamadosLimpos = new JsonArray();
        int abertos = 0;
        int emAlerta = 0;
        DateTimeFormatter formatoDataJira = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

        for (JsonElement item : listaBruta) {
            JsonObject chamado = item.getAsJsonObject();
            JsonObject campos = chamado.getAsJsonObject("fields");

            String chave = chamado.get("key").getAsString();
            String titulo = campos.get("summary").getAsString();
            String status = campos.getAsJsonObject("status").get("name").getAsString();
            String prioridade = (campos.has("priority") && !campos.get("priority").isJsonNull())
                    ? campos.getAsJsonObject("priority").get("name").getAsString()
                    : "Sem prioridade";

            // Calcula quanto tempo o chamado durou ou está aberto
            String criadoStr = campos.get("created").getAsString();
            OffsetDateTime inicio = OffsetDateTime.parse(criadoStr, formatoDataJira);
            boolean estaAberto = campos.get("resolutiondate").isJsonNull();
            OffsetDateTime fim = estaAberto
                    ? OffsetDateTime.now()
                    : OffsetDateTime.parse(campos.get("resolutiondate").getAsString(), formatoDataJira);

            long minutos = Duration.between(inicio, fim).toMinutes();
            boolean alerta = estaAberto && (minutos > 30 || prioridade.equalsIgnoreCase("High") || prioridade.equalsIgnoreCase("Highest"));

            if (estaAberto) abertos++;
            if (alerta) emAlerta++;

            // Monta o bloquinho limpo desse chamado
            JsonObject objLimpo = new JsonObject();
            objLimpo.addProperty("chave", chave);
            objLimpo.addProperty("titulo", titulo);
            objLimpo.addProperty("status", status);
            objLimpo.addProperty("prioridade", prioridade);
            objLimpo.addProperty("aberto", estaAberto);
            objLimpo.addProperty("emAlerta", alerta);
            objLimpo.addProperty("tempoMinutos", minutos);

            chamadosLimpos.add(objLimpo);
        }

        // Passo F: Empacota tudo num JSON final e transforma em texto (String)
        JsonObject resultadoFinal = new JsonObject();
        resultadoFinal.addProperty("responsavel", responsavel);
        resultadoFinal.addProperty("totalChamados", chamadosLimpos.size());
        resultadoFinal.addProperty("totalAbertos", abertos);
        resultadoFinal.addProperty("totalEmAlerta", emAlerta);
        resultadoFinal.add("chamados", chamadosLimpos);

        return gson.toJson(resultadoFinal);
    }
}