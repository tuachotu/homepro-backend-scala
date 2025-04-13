package com.tuachotu.util

// Base exception
class HomeOwnerException(message: String, cause: Throwable = null) extends Exception(message, cause)

// Specialized exceptions
class UserNotFoundException(userId: String)
  extends HomeOwnerException(s"User with ID $userId not found.")

class InvalidInputException(input: String)
  extends HomeOwnerException(s"Invalid input provided: $input")

class UnauthorizedAccessException
  extends HomeOwnerException("Unauthorized access detected.")

