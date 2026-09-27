package com.jagapathi.immichtv.ui.main

sealed interface PeopleUiState {
    data object Loading : PeopleUiState
    data class Error(val message: String) : PeopleUiState
    data class Success(val people: List<PersonUi>) : PeopleUiState
}

data class PersonUi(
    val id: String,
    val name: String,
    val thumbnailUrl: String
)
