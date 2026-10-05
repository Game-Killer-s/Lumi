package com.lumi.app.data.repository

import com.lumi.app.data.model.*
import com.lumi.app.network.*

/**
 * Repository for tariff plans, payments and subscription status.
 */
class SubscriptionRepository {

    private val api = NetworkModule.apiService

    suspend fun getPlans(): ApiResult<List<SubscriptionPlan>> {
        return safeApiCall { api.getSubscriptionPlans() }
    }

    suspend fun getStatus(): ApiResult<SubscriptionStatusResponse> {
        return safeApiCall { api.getSubscriptionStatus() }
    }

    /**
     * Можливості тарифу із сесії: перевіряються перед вибором
     * якості HQ/Lossless і перед завантаженням треку.
     */
    fun getEntitlement(): Entitlement? = TokenManager.getEntitlement()

    suspend fun saveEntitlement(entitlement: Entitlement) {
        TokenManager.saveEntitlement(entitlement)
    }

    /**
     * Оновлює можливості тарифу за станом підписки на сервері.
     * Якщо мережа недоступна — лишаємо те, що вже є в сесії.
     */
    suspend fun refreshEntitlement(): Entitlement {
        return when (val result = getStatus()) {
            is ApiResult.Success -> EntitlementPolicy.fromStatus(result.data).also {
                saveEntitlement(it)
            }
            is ApiResult.Error -> getEntitlement() ?: EntitlementPolicy.free
        }
    }

    suspend fun checkPlayAccess(): ApiResult<PlayAccessResponse> {
        return safeApiCall { api.checkPlayAccess() }
    }

    suspend fun checkout(request: CheckoutRequest): ApiResult<CheckoutResponse> {
        return safeApiCall { api.checkout(request) }
    }

    suspend fun cancel(): ApiResult<CancelSubscriptionResponse> {
        return safeApiCall { api.cancelSubscription() }
    }

    suspend fun getPayments(): ApiResult<List<PaymentHistoryItem>> {
        return safeApiCall { api.getPaymentHistory() }
    }

    suspend fun getPaymentMethods(): ApiResult<List<PaymentMethod>> {
        return safeApiCall { api.getPaymentMethods() }
    }

    suspend fun addPaymentMethod(request: AddPaymentMethodRequest): ApiResult<PaymentMethod> {
        return safeApiCall { api.addPaymentMethod(request) }
    }

    suspend fun updatePaymentMethod(
        paymentMethodId: String,
        request: UpdatePaymentMethodRequest
    ): ApiResult<PaymentMethod> {
        return safeApiCall { api.updatePaymentMethod(paymentMethodId, request) }
    }

    suspend fun removePaymentMethod(paymentMethodId: String): ApiResult<DeletedResponse> {
        return safeApiCall { api.deletePaymentMethod(paymentMethodId) }
    }
}
