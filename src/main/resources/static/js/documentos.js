(function () {
    var uploadForm = null;
    var arquivoInput = null;
    var uploadStatus = null;
    var tabelaBody = null;
    var refreshButton = null;
    var csrfToken = null;
    var csrfHeader = null;

    /**
     * Formata uma data recebida da API para exibicao no padrao local.
     *
     * @param value valor ISO recebido do backend
     * @return data formatada ou o proprio valor quando nao for possivel converter
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
     * Cria o botao de exclusao para uma linha da tabela.
     *
     * @param id identificador do documento a excluir
     * @return elemento button configurado com a acao de exclusao
     */
    function createDeleteButton(id) {
        var deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "delete-button";
        deleteButton.innerHTML = "🗑 Excluir";
        deleteButton.setAttribute("aria-label", "Excluir arquivo");

        deleteButton.addEventListener("click", function () {
            deleteDocumento(id);
        });

        return deleteButton;
    }

    /**
     * Renderiza a lista de documentos na tabela da tela.
     *
     * @param documentos array de documentos retornado pelo backend
     */
    function renderRows(documentos) {
        tabelaBody.innerHTML = "";

        if (!documentos || documentos.length === 0) {
            var empty = document.createElement("tr");
            empty.innerHTML = '<td colspan="7" class="empty-row">Nenhum documento encontrado.</td>';
            tabelaBody.appendChild(empty);
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
                "<td>" + formatDate(doc.dataUpload) + "</td>";

            var actionsTd = document.createElement("td");
            actionsTd.className = "actions-cell";
            actionsTd.appendChild(createDeleteButton(doc.id));

            tr.appendChild(actionsTd);
            tabelaBody.appendChild(tr);
        });
    }

    /**
     * Atualiza a mensagem de status exibida acima da tabela.
     *
     * @param message texto a ser mostrado ao usuario
     * @param isError indica se a mensagem representa erro
     */
    function setStatus(message, isError) {
        uploadStatus.textContent = message;
        uploadStatus.style.color = isError ? "#972d2d" : "#0b3948";
    }

    /**
     * Monta o cabecalho CSRF quando o token esta disponivel na pagina.
     *
     * @return objeto com o header CSRF ou objeto vazio
     */
    function getCsrfHeaders() {
        if (!csrfToken || !csrfHeader) {
            return {};
        }

        var headers = {};
        headers[csrfHeader] = csrfToken;
        return headers;
    }

    /**
     * Carrega a lista de documentos da API e atualiza a tabela.
     */
    function loadDocumentos() {
        setStatus("", false);
        fetch("/api/documentos")
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("Falha ao carregar documentos.");
                }
                return response.json();
            })
            .then(function (data) {
                renderRows(data);
            })
            .catch(function (error) {
                tabelaBody.innerHTML = '<tr><td colspan="7" class="empty-row">' + error.message + "</td></tr>";
            });
    }

    /**
     * Exclui um documento e recarrega a listagem em caso de sucesso.
     *
     * @param id identificador do documento a excluir
     */
    function deleteDocumento(id) {
        if (!id) {
            return;
        }

        var confirmed = window.confirm("Deseja realmente excluir este arquivo?");
        if (!confirmed) {
            return;
        }

        fetch("/api/documentos/" + id, {
            method: "DELETE",
            headers: getCsrfHeaders()
        })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("Nao foi possivel excluir o documento.");
                }
                setStatus("Documento excluido com sucesso.", false);
                loadDocumentos();
            })
            .catch(function (error) {
                setStatus(error.message, true);
            });
    }

    /**
     * Envia o arquivo selecionado para o endpoint de upload.
     *
     * @param event evento de submit do formulario
     */
    function handleUpload(event) {
        event.preventDefault();

        if (!arquivoInput.files || arquivoInput.files.length === 0) {
            setStatus("Selecione um arquivo PDF para upload.", true);
            return;
        }

        var formData = new FormData();
        formData.append("file", arquivoInput.files[0]);

        setStatus("Enviando arquivo...", false);

        fetch("/api/documentos", {
            method: "POST",
            body: formData,
            headers: getCsrfHeaders()
        })
            .then(function (response) {
                if (!response.ok) {
                    return response.text().then(function (message) {
                        throw new Error(message || "Falha no upload.");
                    });
                }
                return response.json();
            })
            .then(function () {
                setStatus("Upload concluido com sucesso.", false);
                uploadForm.reset();
                loadDocumentos();
            })
            .catch(function (error) {
                setStatus(error.message, true);
            });
    }

    /**
     * Inicializa referencias DOM e registra os listeners da tela de documentos.
     */
    function bootstrap() {
        uploadForm = document.getElementById("uploadForm");
        arquivoInput = document.getElementById("arquivoPdf");
        uploadStatus = document.getElementById("uploadStatus");
        tabelaBody = document.getElementById("documentosBody");
        refreshButton = document.getElementById("refreshList");
        csrfToken = document.querySelector('meta[name="_csrf"]')?.getAttribute("content");
        csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.getAttribute("content");

        if (!uploadForm || !arquivoInput || !uploadStatus || !tabelaBody || !refreshButton) {
            return;
        }

        uploadForm.addEventListener("submit", handleUpload);
        refreshButton.addEventListener("click", loadDocumentos);

        loadDocumentos();
    }

    document.addEventListener("DOMContentLoaded", bootstrap);
})();
