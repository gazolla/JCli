package com.gazapps.mcp.matching;

import java.util.Collections;
import java.util.Map;

import com.gazapps.llm.Llm;
import com.gazapps.mcp.domain.Tool;

public class ParameterExtractor {

	public Map<String, Object> extractParameters(String query, Tool tool, Llm llm) {
        // A implementação real aqui envolveria a criação de um prompt detalhado
        // para o LLM, incluindo a query, a descrição da ferramenta e seu schema JSON.
        // A resposta do LLM (geralmente um JSON) seria então analisada para extrair os parâmetros.

        // Por enquanto, esta é uma implementação de placeholder.
        return Collections.emptyMap();
    }
}
