package sa.factory.masarakobdcarrenta.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import sa.factory.masarakobdcarrenta.domain.usecase.*
import sa.factory.masarakobdcarrenta.model.MasarakObdItem
import sa.factory.masarakobdcarrenta.model.MasarakObdOrder
import sa.factory.masarakobdcarrenta.model.MasarakObdTelemetry
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MasarakObdUiState(
    val catalog: List<MasarakObdItem> = emptyList(),
    val filteredCatalog: List<MasarakObdItem> = emptyList(),
    val selectedCategory: String = "الكل",
    val cart: List<MasarakObdItem> = emptyList(),
    val telemetry: MasarakObdTelemetry? = null,
    val lastOrder: MasarakObdOrder? = null,
    val isCheckingOut: Boolean = false,
    val activeTab: String = "home"
)

class MasarakObdViewModel(
    private val getCatalogUseCase: GetCatalogUseCase,
    private val manageCartUseCase: ManageCartUseCase,
    private val processCheckoutUseCase: ProcessCheckoutUseCase,
    private val observeHardwareTelemetryUseCase: ObserveHardwareTelemetryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MasarakObdUiState())
    val uiState: StateFlow<MasarakObdUiState> = _uiState.asStateFlow()

    init {
        // Observe Catalog
        getCatalogUseCase().onEach { list ->
            _uiState.update { it.copy(catalog = list, filteredCatalog = list) }
        }.launchIn(viewModelScope)

        // Observe Cart
        manageCartUseCase.cart.onEach { cartItems ->
            _uiState.update { it.copy(cart = cartItems) }
        }.launchIn(viewModelScope)

        // Observe Telemetry
        observeHardwareTelemetryUseCase().onEach { telem ->
            _uiState.update { it.copy(telemetry = telem) }
        }.launchIn(viewModelScope)
    }

    fun selectCategory(category: String) {
        _uiState.update { state ->
            val filtered = if (category == "الكل") state.catalog else state.catalog.filter { it.category == category }
            state.copy(selectedCategory = category, filteredCatalog = filtered)
        }
    }

    fun switchTab(tab: String) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun addToCart(item: MasarakObdItem) = manageCartUseCase.add(item)
    fun removeFromCart(item: MasarakObdItem) = manageCartUseCase.remove(item)

    fun checkout(paymentMethod: String = "Mada Contactless EMV") {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingOut = true) }
            val order = processCheckoutUseCase(paymentMethod)
            _uiState.update { it.copy(isCheckingOut = false, lastOrder = order, activeTab = "checkout") }
        }
    }
}
