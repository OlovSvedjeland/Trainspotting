jcp() {
    java -cp bin Main Lab1.map $1 $2 1
}

run() {
    make
    jcp 5 15 & jcp 5 1 & jcp 7 1 & jcp 5 10 & jcp 2 5 & jcp 1 10 & jcp 4 2 & jcp 7 14
}