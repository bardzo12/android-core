package sk.pixwell.android.core.domain

import arrow.core.Either
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.ReplaySubject
import io.reactivex.subjects.Subject

abstract class AuthenticatedUseCase<A, P : UseCase.Params, T : Any> : UseCase<P, T>() {
    open val autoLogin = false
    open val oneShot: Boolean = true

    private lateinit var source: Subject<T>
    private var authDisposable: Disposable? = null
    private var authSuccessDisposable: Disposable? = null

    abstract fun buildAuth(): Observable<Either<AuthError, A>>

    abstract fun goToLogin()

    abstract fun onAuthError(error: AuthError): T

    abstract fun onAuthSuccess(token: A): Observable<T>

    private fun authenticate() {
        authDisposable?.dispose()
        authDisposable = buildAuth()
            .subscribe({ it.fold(::handleAuthError, ::handleAuthSuccess) }, ::handleError)
    }

    override fun build(): Observable<T> {
        source = ReplaySubject.create()
        instances.add(this)
        authenticate()
        return source
    }

    private fun handleAuthError(error: AuthError) {
        source.onNext(onAuthError(error))
        when (error) {
            AuthError.NotAuthenticatedError -> {
                if (autoLogin) goToLogin()
            }
        }
    }

    private fun handleAuthSuccess(token: A) {
        if (oneShot) instances.remove(this)
        authSuccessDisposable?.dispose()
        authSuccessDisposable = onAuthSuccess(token)
            .subscribe({
                source.onNext(it)
                if (oneShot) source.onComplete()
            }, ::handleError)
    }

    override fun clear() {
        super.clear()
        instances.remove(this)
        authDisposable?.dispose()
        authSuccessDisposable?.dispose()
    }

    companion object {
        private val instances = mutableSetOf<AuthenticatedUseCase<*, *, *>>()

        fun notifyAuthStatusChanged() {
            instances.forEach { it.authenticate() }
        }
    }
}

sealed class AuthError {
    object NotAuthenticatedError : AuthError()
    data class OtherError(val msg: String) : AuthError()
}