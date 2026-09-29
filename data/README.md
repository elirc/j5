# Fixtures

`expenses.txt` has six rows: one with an empty note (row 2 and 5) and one whose note contains an escaped tab (row 3, `Gift for\tthe office`). `corrupt-example.txt` has a non-numeric amount on row 2, so loading it must fail without touching any existing file. Copy either before pointing the app at it.
