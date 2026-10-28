package br.com.desenvolvedorgustavolopes.moneyPilot.category;

import br.com.desenvolvedorgustavolopes.moneyPilot.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Categorias", description = "Categorias padrão (somente leitura) e as criadas pela pessoa.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/categories")
public class CategoryController {

    private final CategoryService service;

    @Operation(summary = "Lista as categorias", description = "Lista paginada: use page, size e sort (ex.: sort=name,asc).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Página de categorias"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<CategoryResponse> getAllCategories(Pageable pageable) {
        return service.getAllCategories(pageable);
    }

    @Operation(summary = "Cria uma categoria", description = "Cria uma categoria própria de despesa ou receita.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Categoria criada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content)
    })
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return service.createCategory(request);
    }

    @Operation(summary = "Busca uma categoria", description = "Devolve uma categoria pelo id.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria encontrada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public CategoryResponse getCategoryById(@PathVariable Long id) {
        return service.getCategoryById(id);
    }

    @Operation(summary = "Atualiza uma categoria", description = "Só categorias criadas pela pessoa podem mudar; as padrão respondem 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Categoria atualizada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos ou regra de negócio violada", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Operação não permitida", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public CategoryResponse updateCategory(@PathVariable Long id, @RequestBody @Valid CategoryRequest request) {
        return service.updateOwnedCategory(id, request);
    }

    @Operation(summary = "Apaga uma categoria", description = "Só categorias criadas pela pessoa podem ser apagadas; as padrão respondem 403.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Categoria apagada"),
            @ApiResponse(responseCode = "401", description = "Sem sessão ou token de acesso inválido ou vencido", content = @Content),
            @ApiResponse(responseCode = "403", description = "Operação não permitida", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Recurso não encontrado (ou de outra conta)", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Conflito com o estado atual do recurso", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable Long id) {
        service.deleteUserCategory(id);
    }
}
