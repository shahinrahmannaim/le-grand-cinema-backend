package fr.legrandcinema.shared;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ProblemDetail business(ApiException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(e.status()), e.getMessage());
    }

    @ExceptionHandler({NoSuchElementException.class, EmptyResultDataAccessException.class})
    ProblemDetail notFound(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Ressource introuvable.");
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        IllegalArgumentException.class,
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class
    })
    ProblemDetail invalidRequest(Exception e) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Paramètres invalides. Vérifiez les champs obligatoires et leurs limites.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail conflict(DataIntegrityViolationException e) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Conflit : place déjà réservée, créneau occupé ou donnée déjà utilisée. Actualisez"
                    + " avant de réessayer.");
    }
}
