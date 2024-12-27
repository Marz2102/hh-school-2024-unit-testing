package ru.hh.school.unittesting.homework;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LibraryManagerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    @InjectMocks
    private LibraryManager libraryManager;

    @BeforeEach
    void setUp(){
        libraryManager = new LibraryManager(notificationService, userService);
        libraryManager.addBook("book1", 10);
        libraryManager.addBook("book2", 3);
        libraryManager.addBook("book3", 14);
        libraryManager.addBook("book4", 0);
    }

    @Test
    void testAddOneBook(){
        libraryManager.addBook("book4", 8);
        assertEquals(libraryManager.getAvailableCopies("book4"), 8);
    }

    @ParameterizedTest
    @CsvSource({
            "book1, 1, 11",
            "book2, 10, 13",
            "book3, 0, 14",
            "book4, 10, 10"
    })
    void testAddSeveralBooks(String bookId, int quantity, int expectedCount){
        libraryManager.addBook(bookId, quantity);
        assertEquals(libraryManager.getAvailableCopies(bookId), expectedCount);
    }

    @ParameterizedTest
    @CsvSource({
        "book5, 1, 1",
        "book6, 15, 15",
        "book10, 3, 3"
    })
    void testAddNewBooks(String bookId, int quantity, int expectedCount){
        libraryManager.addBook(bookId, quantity);
        assertEquals(libraryManager.getAvailableCopies(bookId), expectedCount);
    }

    @Test
    void testAddNegativeCountOfBook(){
        //Такого быть не должно, что можно добавить новую книгу с отрицательным количеством экземпляров
        libraryManager.addBook("book5", -1);
        assertEquals(libraryManager.getAvailableCopies("book5"), -1);
    }

    @ParameterizedTest
    @CsvSource({
            "book1, user1",
            "book2, user1",
            "book10, user5"
    })
    void testTakeBookByNotActiveUser(String bookId, String userId){
        when(userService.isUserActive(anyString())).thenReturn(false);
        boolean borrowResult = libraryManager.borrowBook(bookId, userId);

        assertFalse(borrowResult);
        verify(notificationService).notifyUser(userId,"Your account is not active.");
        verifyNoMoreInteractions(notificationService);
        verifyNoMoreInteractions(userService);
    }

    @Test
    void testTakeNotAvailableBook(){
        String userId = "user1";
        String bookId = "book4";
        when(userService.isUserActive(userId)).thenReturn(true);
        boolean borrowResult = libraryManager.borrowBook(bookId, userId);

        assertFalse(borrowResult);
        verifyNoMoreInteractions(notificationService);
        verifyNoMoreInteractions(userService);
    }

    @ParameterizedTest
    @CsvSource({
            "book1, user1, 9, true",
            "book2, user3, 2, true",
            "book3, user3, 13, true",
            "book4, user4, 0, false"
    })
    void testBorrowBook(String bookId, String userId, int expectedCount, boolean expectedResult){
        when(userService.isUserActive(anyString())).thenReturn(true);
        boolean borrowResult = libraryManager.borrowBook(bookId, userId);

        assertEquals(borrowResult, expectedResult);
        assertEquals(libraryManager.getAvailableCopies(bookId), expectedCount);

        if(expectedResult){
           verify(notificationService).notifyUser(userId, "You have borrowed the book: " + bookId);
        }
        verifyNoMoreInteractions(notificationService);
        verifyNoMoreInteractions(userService);
    }

    @Test
    void testReturnBookNotFromLibrary(){
        boolean returnResult = libraryManager.returnBook("book5", "user1");

        assertFalse(returnResult);
    }

    @Test
    void testReturnSomeonesBook(){
        String bookId = "book1";
        String userId1 = "user1", userId2 = "user2";
        when(userService.isUserActive(anyString())).thenReturn(true);
        libraryManager.borrowBook(bookId, userId1);

        boolean returnResult = libraryManager.returnBook(bookId, userId2);
        assertEquals(libraryManager.getAvailableCopies(bookId), 9);
        assertFalse(returnResult);
    }

    @ParameterizedTest
    @CsvSource({
            "book1, user1, 0, 9",
            "book1, user2, 0, 9",
            "book2, user1, 0, 2",
            "book3, user4, 0, 13",
            "book4, user1, 1, 0"
    })
    void testReturnBook(String bookId, String userId, int quantity, int expectedCount){
        when(userService.isUserActive(userId)).thenReturn(true);
        libraryManager.addBook(bookId, quantity);
        libraryManager.borrowBook(bookId, userId);

        assertEquals(libraryManager.getAvailableCopies(bookId), expectedCount);
        boolean returnResult = libraryManager.returnBook(bookId, userId);
        assertTrue(returnResult);
        verify(notificationService).notifyUser(userId, "You have returned the book: " + bookId);
    }

    @Test
    void testCalculateDynamicLateFeeGetException(){
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> libraryManager.calculateDynamicLateFee(-10, true, false)
        );
        assertEquals(exception.getMessage(), "Overdue days cannot be negative.");
    }

    @ParameterizedTest
    @CsvSource({
            "1, true, true, 0.6",
            "10, true, true, 6.",
            "0, false, true, 0.",
            "0, true, false, 0.",
            "3, false, true, 1.2",
            "2, true, false, 1.5",
            "8, false, false, 4.",
            "1, false, false, 0.5",
            "0, true, true, 0",
            "0, false, false, 0.",
            "7, true, false, 5.25",
            "4, false, true, 1.6",
            "365, true, true, 219"
    })
    void testCalculateGetDynamicLateFee(int overdueDays, boolean isBestseller, boolean isPremiumNumber, double expectedValue){
        assertEquals(libraryManager.calculateDynamicLateFee(overdueDays, isBestseller, isPremiumNumber), expectedValue);
        verifyNoMoreInteractions(notificationService);
        verifyNoMoreInteractions(userService);
    }
}