# Compose 와 Hilt 는 각자 consumer 규칙을 함께 배포하므로 별도 keep 규칙이 필요 없다.
# 릴리스에서 스택트레이스를 읽을 수 있게 줄 번호만 남긴다.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
