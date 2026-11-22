package bio.trace;

public class Teste {



        public static void main(String[] args) {

            JiraClient jiraClient = new JiraClient();

            try {
                String resultado =
                        jiraClient.buscarChamadosPorResponsavel("Ana Clara Ferreira Clarete");

                System.out.println("RESPOSTA DO JIRA:");
                System.out.println(resultado);

            } catch (Exception e) {
                System.out.println("Erro ao conectar com Jira:");
                e.printStackTrace();
            }
        }

}
