(function () {
    var form = null;
    var status = null;
    var resultadosBody = null;
    var recarregarBtn = null;
    var limparBtn = null;
    var ultimaUrlConsulta = null;

    /**
     * Formata uma data ISO para o idioma pt-BR.
     *
     * @param value valor recebido do backend
     * @return data formatada ou o valor original quando a conversao falhar
     */
    function formatDate(value) {
        if (!value) {
            return "-";
        }

        var date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }

        return date.toLocaleString("pt-BR");
    }

    /**
     * Atualiza o texto de status da tela de consulta.
     *
     * @param message mensagem a ser exibida
     * @param isError indica se o status representa falha
     */
    function setStatus(message, isError) {
        status.textContent = message || "";
        status.style.color = isError ? "#972d2d" : "#114b5f";
    }

    /**
     * Renderiza os resultados da consulta na tabela.
     *
     * @param documentos lista retornada pela API de busca
     */
    function renderResultados(documentos) {
        resultadosBody.innerHTML = "";

        if (!documentos || documentos.length === 0) {
            resultadosBody.innerHTML = '<tr><td colspan="8" class="empty-row">Nenhum documento encontrado.</td></tr>';
            return;
        }

        documentos.forEach(function (doc) {
            var tr = document.createElement("tr");
            var statusClass = (doc.status || "").toUpperCase();

            tr.innerHTML =
                "<td>" + (doc.id || "-") + "</td>" +
                "<td>" + (doc.nomeOriginal || "-") + "</td>" +
                "<td><span class=\"status-pill " + statusClass + "\">" + (doc.status || "-") + "</span></td>" +
                "<td>" + (doc.numeroProcesso || "-") + "</td>" +
                "<td>" + (doc.materia || "-") + "</td>" +
                "<td>" + (doc.origemExtracao || (doc.textoNativo ? "PDFBOX" : "TESSERACT") || "-") + "</td>" +
                "<td>" + formatDate(doc.dataUpload) + "</td>";

            var actionsTd = document.createElement("td");
            actionsTd.className = "actions-cell";

            var downloadLink = document.createElement("a");
            downloadLink.className = "btn btn-secondary btn-sm";
            downloadLink.textContent = "Baixar PDF";
            downloadLink.href = "/api/documentos/" + doc.id + "/download";
            downloadLink.target = "_blank";
            downloadLink.rel = "noopener";

            actionsTd.appendChild(downloadLink);
            tr.appendChild(actionsTd);

            resultadosBody.appendChild(tr);
        });
    }

    /**
     * Monta a URL de consulta a partir dos campos do formulario.
     *
     * @param formData dados atuais do formulario de busca
     * @return URL relativa pronta para chamada fetch
     */
    function buildQueryString(formData) {
        var params = new URLSearchParams();

        ["termo", "status", "numeroProcesso", "materia", "dataInicio", "dataFim"].forEach(function (field) {
            var value = formData.get(field);
            if (value) {
                params.append(field, value);
            }
        });

        var query = params.toString();
        return query ? "/api/documentos/busca?" + query : "/api/documentos";
    }

    /**
     * Executa a chamada ao backend e atualiza a tabela de resultados.
     *
     * @param url URL da consulta a ser executada
     */
    function executarConsulta(url) {
        ultimaUrlConsulta = url;
        setStatus("Consultando backend...", false);

        fetch(url)
            .then(function (response) {
                if (!response.ok) {
                    return response.text().then(function (message) {
                        throw new Error(message || "Falha na consulta.");
                    });
                }
                return response.json();
            })
            .then(function (data) {
                renderResultados(data);
                setStatus("Consulta realizada com sucesso.", false);
            })
            .catch(function (error) {
                resultadosBody.innerHTML = '<tr><td colspan="8" class="empty-row">' + error.message + "</td></tr>";
                setStatus(error.message, true);
            });
    }

    /**
     * Dispara uma nova consulta ao submeter o formulario.
     *
     * @param event evento submit do formulario
     */
    function onSubmit(event) {
        event.preventDefault();

        var formData = new FormData(form);
        executarConsulta(buildQueryString(formData));
    }

    /**
     * Reexecuta a ultima consulta realizada ou carrega a listagem padrao.
     */
    function onRecarregar() {
        if (ultimaUrlConsulta) {
            executarConsulta(ultimaUrlConsulta);
            return;
        }

        executarConsulta("/api/documentos");
    }

    /**
     * Limpa os filtros e volta a tela para o estado inicial.
     */
    function onLimpar() {
        form.reset();
        ultimaUrlConsulta = null;
        resultadosBody.innerHTML = '<tr><td colspan="8" class="empty-row">Clique em pesquisar para carregar os documentos.</td></tr>';
        setStatus("Filtros limpos.", false);
    }

    /**
     * Localiza os elementos da tela e registra os listeners da consulta.
     */
    function bootstrap() {
        form = document.getElementById("consultaForm");
        status = document.getElementById("consultaStatus");
        resultadosBody = document.getElementById("consultaResultadosBody");
        recarregarBtn = document.getElementById("recarregarConsulta");
        limparBtn = document.getElementById("limparConsulta");

        if (!form || !status || !resultadosBody || !recarregarBtn || !limparBtn) {
            return;
        }

        form.addEventListener("submit", onSubmit);
        recarregarBtn.addEventListener("click", onRecarregar);
        limparBtn.addEventListener("click", onLimpar);
    }

    document.addEventListener("DOMContentLoaded", bootstrap);
})();
