/**
 * Configuração modular do projeto Pomodoro.
 * Estabelece as dependências de runtime, permissões de reflexão para frameworks 
 * e a exposição da API do sistema.
 */
module com.thomazcollet.pomodoro {
    // Dependências de Terceiros e JDK
    requires javafx.controls;
    requires javafx.fxml;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires java.sql;

    // Permissões de Introspecção e Reflexão
    // Aberto para permitir que frameworks de teste (JUnit/Mockito) acessem a camada de serviço
    opens com.thomazcollet.service; 

    // Aberto para o JavaFX permitir o mapeamento de Controllers e carregamento de FXML
    opens com.thomazcollet to javafx.fxml;
    opens com.thomazcollet.ui to javafx.fxml;

    // Exportação da API Pública do Módulo
    exports com.thomazcollet;
}