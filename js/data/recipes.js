// 레시피 데이터 (4인 가족: 어른 2 + 유아 2 기준 분량)
//
// 재료 형식: [이름, 수량, 단위, 카테고리]
//   - 수량 0 = "약간"
//   - 같은 이름+단위의 재료는 장보기 목록에서 합산되므로 이름을 통일해서 쓸 것
//
// 레시피 필드:
//   type     : 'breakfast' | 'main'(한식 메인) | 'soup'(국·찌개) | 'western'(양식 저녁)
//   time     : 조리 시간(분)
//   protein  : beef | pork | chicken | fish | seafood | tofu | egg | veg  (연속 같은 단백질 회피용)
//   spicy    : 매운 요리 여부 (주간 횟수 제한 대상)
//   oneDish  : 한 그릇 요리 (국 없이 추천)
//   base     : 아침 종류 (bread | cereal | rice | etc) — 연속 같은 종류 회피용
//   kidTip   : 아이들(6세·4세) 먹일 때 팁
//   kit      : 밀키트 (평일 저녁에 주 N회 배치, 재료는 밀키트 1개 + 추가 재료)

export const CATEGORIES = [
  { key: 'kit', label: '밀키트', emoji: '🎁' },
  { key: 'veg', label: '채소', emoji: '🥬' },
  { key: 'fruit', label: '과일', emoji: '🍎' },
  { key: 'meat', label: '정육', emoji: '🥩' },
  { key: 'sea', label: '수산', emoji: '🐟' },
  { key: 'egg', label: '계란·유제품', emoji: '🥚' },
  { key: 'proc', label: '두부·가공식품', emoji: '🧈' },
  { key: 'grain', label: '빵·면·떡·시리얼', emoji: '🍞' },
  { key: 'etc', label: '기타', emoji: '🛒' },
  { key: 'pantry', label: '기본 양념 (집에 있는지 확인)', emoji: '🧂' },
];

export const RECIPES = [
  // ───────────────────────────── 아침 ─────────────────────────────
  {
    id: 'b-butter-toast', type: 'breakfast', name: '버터 토스트 & 우유', emoji: '🍞', time: 5, base: 'bread', protein: 'etc',
    ingredients: [['식빵', 8, '장', 'grain'], ['버터', 30, 'g', 'egg'], ['우유', 800, 'ml', 'egg'], ['딸기잼', 0, '', 'pantry']],
    steps: ['식빵을 토스터에 노릇하게 굽는다.', '버터나 잼을 얇게 발라준다.', '우유, 과일과 함께 낸다.'],
  },
  {
    id: 'b-egg-toast', type: 'breakfast', name: '양배추 계란 토스트', emoji: '🥪', time: 10, base: 'bread', protein: 'egg',
    ingredients: [['식빵', 8, '장', 'grain'], ['계란', 4, '개', 'egg'], ['양배추', 100, 'g', 'veg'], ['당근', 0.3, '개', 'veg'], ['버터', 20, 'g', 'egg'], ['케첩', 0, '', 'pantry'], ['설탕', 0, '', 'pantry']],
    steps: ['양배추·당근을 곱게 채 썰어 계란, 소금 약간과 섞는다.', '버터 두른 팬에 식빵 크기로 부친다.', '구운 식빵 사이에 넣고 케첩·설탕을 살짝 뿌린다.'],
    kidTip: '채소를 아주 가늘게 썰면 아이들이 잘 못 느껴요.',
  },
  {
    id: 'b-french-toast', type: 'breakfast', name: '프렌치 토스트', emoji: '🍯', time: 15, base: 'bread', protein: 'egg',
    ingredients: [['식빵', 8, '장', 'grain'], ['계란', 3, '개', 'egg'], ['우유', 150, 'ml', 'egg'], ['버터', 20, 'g', 'egg'], ['설탕', 1, '큰술', 'pantry']],
    steps: ['계란, 우유, 설탕을 잘 풀어 섞는다.', '식빵을 앞뒤로 충분히 적신다.', '버터 두른 약불 팬에서 노릇하게 굽는다.'],
    kidTip: '한입 크기로 잘라주면 포크로 혼자 잘 먹어요.',
  },
  {
    id: 'b-cereal', type: 'breakfast', name: '시리얼 & 우유', emoji: '🥣', time: 3, base: 'cereal', protein: 'etc',
    ingredients: [['시리얼', 200, 'g', 'grain'], ['우유', 800, 'ml', 'egg']],
    steps: ['그릇에 시리얼을 담고 우유를 붓는다.', '썬 과일을 위에 올리면 더 좋아요.'],
  },
  {
    id: 'b-granola', type: 'breakfast', name: '그래놀라 요거트', emoji: '🥛', time: 3, base: 'cereal', protein: 'etc',
    ingredients: [['그래놀라', 200, 'g', 'grain'], ['플레인 요거트', 800, 'g', 'egg'], ['꿀', 0, '', 'pantry']],
    steps: ['요거트를 그릇에 담는다.', '그래놀라와 썬 과일을 올리고 꿀을 조금 뿌린다.'],
  },
  {
    id: 'b-ham-cheese', type: 'breakfast', name: '햄치즈 샌드위치', emoji: '🥪', time: 10, base: 'bread', protein: 'pork',
    ingredients: [['식빵', 8, '장', 'grain'], ['슬라이스 햄', 8, '장', 'proc'], ['슬라이스 치즈', 8, '장', 'egg'], ['버터', 15, 'g', 'egg']],
    steps: ['식빵에 버터를 얇게 바른다.', '햄과 치즈를 넣고 덮는다.', '팬이나 토스터에 살짝 구워 치즈를 녹인다.'],
  },
  {
    id: 'b-scramble', type: 'breakfast', name: '모닝빵 & 스크램블에그', emoji: '🍳', time: 10, base: 'bread', protein: 'egg',
    ingredients: [['모닝빵', 8, '개', 'grain'], ['계란', 5, '개', 'egg'], ['우유', 50, 'ml', 'egg'], ['버터', 15, 'g', 'egg'], ['소금', 0, '', 'pantry']],
    steps: ['계란, 우유, 소금 한 꼬집을 섞는다.', '버터 두른 약불 팬에서 젓가락으로 저어가며 익힌다.', '모닝빵을 반 갈라 끼워준다.'],
  },
  {
    id: 'b-pancake', type: 'breakfast', name: '핫케이크', emoji: '🥞', time: 20, base: 'bread', protein: 'egg',
    ingredients: [['핫케이크 가루', 500, 'g', 'grain'], ['계란', 2, '개', 'egg'], ['우유', 300, 'ml', 'egg'], ['버터', 15, 'g', 'egg'], ['메이플시럽', 0, '', 'pantry']],
    steps: ['핫케이크 가루, 계란, 우유를 섞어 반죽한다.', '약불 팬에 한 국자씩 올려 기포가 올라오면 뒤집는다.', '버터와 시럽을 곁들인다.'],
    kidTip: '작은 사이즈로 여러 장 굽고 과일로 얼굴을 꾸며주면 좋아해요.',
  },
  {
    id: 'b-oatmeal', type: 'breakfast', name: '바나나 오트밀', emoji: '🍌', time: 10, base: 'cereal', protein: 'etc',
    ingredients: [['오트밀', 160, 'g', 'grain'], ['우유', 800, 'ml', 'egg'], ['바나나', 2, '개', 'fruit']],
    steps: ['냄비에 우유와 오트밀을 넣고 약불에서 5분 저어가며 끓인다.', '으깬 바나나를 넣어 단맛을 낸다.'],
  },
  {
    id: 'b-jumeokbap', type: 'breakfast', name: '참치마요 주먹밥', emoji: '🍙', time: 15, base: 'rice', protein: 'fish',
    ingredients: [['밥', 3, '공기', 'pantry'], ['참치캔', 1, '캔', 'proc'], ['김가루', 1, '봉', 'sea'], ['마요네즈', 2, '큰술', 'pantry'], ['참기름', 1, '큰술', 'pantry']],
    steps: ['참치는 기름을 빼고 마요네즈와 섞는다.', '밥에 참기름, 소금 약간, 김가루를 섞는다.', '참치마요를 속에 넣고 동그랗게 뭉친다.'],
    kidTip: '아이 손 크기로 작게 뭉치면 혼자 잘 먹어요. 전날 밤 만들어 랩에 싸두면 편해요.',
  },
  {
    id: 'b-egg-rice', type: 'breakfast', name: '간장 계란밥', emoji: '🍚', time: 5, base: 'rice', protein: 'egg',
    ingredients: [['밥', 3, '공기', 'pantry'], ['계란', 4, '개', 'egg'], ['조미김', 1, '봉', 'sea'], ['간장', 1, '큰술', 'pantry'], ['참기름', 1, '큰술', 'pantry']],
    steps: ['계란 프라이를 한다.', '따뜻한 밥 위에 올리고 간장·참기름을 조금씩 두른다.', '김을 부숴 올려 비벼 먹는다.'],
    kidTip: '아이들은 노른자까지 완전히 익혀주세요.',
  },
  {
    id: 'b-nurungji', type: 'breakfast', name: '누룽지', emoji: '🥘', time: 10, base: 'rice', protein: 'etc',
    ingredients: [['누룽지', 200, 'g', 'grain'], ['조미김', 1, '봉', 'sea']],
    steps: ['냄비에 물과 누룽지를 넣고 7~8분 끓인다.', '김, 김치 등 가벼운 반찬과 함께 낸다.'],
    kidTip: '뜨거우니 덜어서 한 김 식혀주세요.',
  },
  {
    id: 'b-sweetpotato', type: 'breakfast', name: '군고구마 & 우유', emoji: '🍠', time: 5, base: 'etc', protein: 'etc',
    ingredients: [['고구마', 4, '개', 'veg'], ['우유', 800, 'ml', 'egg']],
    steps: ['전날 밤 에어프라이어 200℃에서 35~40분 구워둔다.', '아침에 전자레인지로 살짝 데워 우유와 함께 낸다.'],
  },
  {
    id: 'b-bagel', type: 'breakfast', name: '베이글 & 크림치즈', emoji: '🥯', time: 5, base: 'bread', protein: 'etc',
    ingredients: [['베이글', 4, '개', 'grain'], ['크림치즈', 100, 'g', 'egg']],
    steps: ['베이글을 반으로 갈라 토스터에 굽는다.', '크림치즈를 바른다.'],
    kidTip: '베이글은 질기니 아이들 몫은 작게 잘라주세요.',
  },
  {
    id: 'b-beef-porridge', type: 'breakfast', name: '소고기 야채죽', emoji: '🥣', time: 25, base: 'rice', protein: 'beef',
    ingredients: [['밥', 2, '공기', 'pantry'], ['다진 소고기', 100, 'g', 'meat'], ['당근', 0.3, '개', 'veg'], ['애호박', 0.3, '개', 'veg'], ['참기름', 1, '큰술', 'pantry'], ['국간장', 0.5, '큰술', 'pantry']],
    steps: ['참기름에 다진 소고기와 잘게 썬 채소를 볶는다.', '밥과 물(밥의 3배)을 넣고 저어가며 15분 끓인다.', '국간장이나 소금으로 살짝 간한다.'],
  },
  {
    id: 'b-garaetteok', type: 'breakfast', name: '가래떡 구이 & 우유', emoji: '🍡', time: 10, base: 'etc', protein: 'etc',
    ingredients: [['가래떡', 400, 'g', 'grain'], ['우유', 800, 'ml', 'egg'], ['꿀', 0, '', 'pantry']],
    steps: ['가래떡을 팬이나 에어프라이어에 노릇하게 굽는다.', '꿀을 찍어 먹는다.'],
    kidTip: '떡은 목에 걸리기 쉬우니 4세는 잘게 잘라 앉아서 천천히 먹게 해주세요.',
  },
  {
    id: 'b-yubu', type: 'breakfast', name: '유부초밥', emoji: '🍣', time: 15, base: 'rice', protein: 'tofu',
    ingredients: [['유부초밥 키트', 1, '봉', 'proc'], ['밥', 3, '공기', 'pantry']],
    steps: ['밥에 키트의 배합초와 후레이크를 섞는다.', '유부의 물기를 살짝 짜고 밥을 채운다.'],
    kidTip: '아이들과 같이 만들면 아침부터 즐거워해요.',
  },
  {
    id: 'b-egg-jjim', type: 'breakfast', name: '계란찜 & 밥', emoji: '🥚', time: 15, base: 'rice', protein: 'egg',
    ingredients: [['계란', 5, '개', 'egg'], ['대파', 0.2, '대', 'veg'], ['밥', 3, '공기', 'pantry'], ['조미김', 1, '봉', 'sea'], ['새우젓', 0, '', 'pantry']],
    steps: ['계란에 물(계란과 같은 양), 새우젓이나 소금을 넣고 푼다.', '뚝배기에서 저어가며 익히다 몽글해지면 뚜껑 덮고 약불 3분.', '송송 썬 대파를 올린다.'],
  },

  // ─────────────────────────── 한식 메인 ───────────────────────────
  {
    id: 'k-bulgogi', type: 'main', cuisine: 'korean', name: '소불고기', emoji: '🥩', time: 30, protein: 'beef',
    ingredients: [['소고기 불고기용', 500, 'g', 'meat'], ['양파', 1, '개', 'veg'], ['당근', 0.5, '개', 'veg'], ['대파', 1, '대', 'veg'], ['팽이버섯', 1, '봉', 'veg'], ['배', 0.25, '개', 'fruit'],
      ['간장', 5, '큰술', 'pantry'], ['설탕', 2, '큰술', 'pantry'], ['다진마늘', 1, '큰술', 'veg'], ['참기름', 1, '큰술', 'pantry'], ['후추', 0, '', 'pantry']],
    steps: ['고기는 키친타월로 핏물을 닦는다.', '간장, 설탕, 다진마늘, 간 배, 참기름, 후추로 양념해 20분 이상 재운다.', '채소를 썰어 넣고 센불에서 국물이 자작해질 때까지 볶는다.'],
    kidTip: '전날 밤 재워두면 저녁 15분 컷. 남으면 다음날 불고기 덮밥으로.',
  },
  {
    id: 'k-pork-bulgogi', type: 'main', cuisine: 'korean', name: '간장 돼지불고기', emoji: '🐷', time: 25, protein: 'pork',
    ingredients: [['돼지고기 앞다리(불고기용)', 500, 'g', 'meat'], ['양파', 1, '개', 'veg'], ['대파', 1, '대', 'veg'], ['당근', 0.5, '개', 'veg'],
      ['간장', 4, '큰술', 'pantry'], ['설탕', 1.5, '큰술', 'pantry'], ['맛술', 2, '큰술', 'pantry'], ['다진마늘', 1, '큰술', 'veg'], ['참기름', 1, '큰술', 'pantry']],
    steps: ['고기에 간장, 설탕, 맛술, 다진마늘을 넣고 10분 재운다.', '팬에 고기를 먼저 볶다가 채소를 넣는다.', '참기름을 두르고 마무리한다.'],
  },
  {
    id: 'k-jeyuk', type: 'main', cuisine: 'korean', name: '제육볶음', emoji: '🌶️', time: 25, protein: 'pork', spicy: true,
    ingredients: [['돼지고기 앞다리(불고기용)', 500, 'g', 'meat'], ['양파', 1, '개', 'veg'], ['대파', 1, '대', 'veg'], ['양배추', 150, 'g', 'veg'],
      ['고추장', 2, '큰술', 'pantry'], ['고춧가루', 1, '큰술', 'pantry'], ['간장', 2, '큰술', 'pantry'], ['설탕', 1.5, '큰술', 'pantry'], ['다진마늘', 1, '큰술', 'veg'], ['참기름', 1, '큰술', 'pantry']],
    steps: ['고기를 간장, 설탕, 다진마늘로 먼저 볶는다.', '채소를 넣고 숨이 죽으면 고추장, 고춧가루를 넣어 볶는다.', '참기름으로 마무리.'],
    kidTip: '고추장 넣기 전에 아이 몫을 먼저 덜어두면 간장 불고기가 돼요.',
  },
  {
    id: 'k-dakbokkeum', type: 'main', cuisine: 'korean', name: '닭볶음탕', emoji: '🍗', time: 45, protein: 'chicken', spicy: true,
    ingredients: [['닭볶음탕용 닭', 1000, 'g', 'meat'], ['감자', 2, '개', 'veg'], ['당근', 1, '개', 'veg'], ['양파', 1, '개', 'veg'], ['대파', 1, '대', 'veg'],
      ['고춧가루', 2, '큰술', 'pantry'], ['고추장', 1, '큰술', 'pantry'], ['간장', 4, '큰술', 'pantry'], ['설탕', 1, '큰술', 'pantry'], ['다진마늘', 1.5, '큰술', 'veg']],
    steps: ['닭은 끓는 물에 한 번 데쳐 헹군다.', '닭, 물 3컵, 간장, 설탕, 다진마늘을 넣고 15분 끓인다.', '감자·당근을 넣고 10분, 고춧가루·고추장·양파·대파를 넣고 10분 더 졸인다.'],
    kidTip: '고춧가루 넣기 전에 닭과 감자를 덜어두면 아이용 간장 닭조림이 돼요.',
  },
  {
    id: 'k-jjimdak', type: 'main', cuisine: 'korean', name: '간장 찜닭', emoji: '🍗', time: 45, protein: 'chicken',
    ingredients: [['닭볶음탕용 닭', 1000, 'g', 'meat'], ['감자', 2, '개', 'veg'], ['당근', 1, '개', 'veg'], ['양파', 1, '개', 'veg'], ['대파', 1, '대', 'veg'], ['당면', 50, 'g', 'grain'],
      ['간장', 6, '큰술', 'pantry'], ['설탕', 2, '큰술', 'pantry'], ['올리고당', 1, '큰술', 'pantry'], ['다진마늘', 1.5, '큰술', 'veg']],
    steps: ['당면은 미지근한 물에 30분 불린다.', '데친 닭에 물 3컵, 간장, 설탕, 다진마늘을 넣고 15분 끓인다.', '감자·당근·양파를 넣고 15분, 당면·대파·올리고당을 넣고 5분 더 졸인다.'],
    kidTip: '맵지 않아서 온 가족이 같이 먹기 좋아요.',
  },
  {
    id: 'k-dakgalbi', type: 'main', cuisine: 'korean', name: '닭갈비', emoji: '🔥', time: 30, protein: 'chicken', spicy: true,
    ingredients: [['닭다리살', 600, 'g', 'meat'], ['양배추', 300, 'g', 'veg'], ['고구마', 1, '개', 'veg'], ['양파', 1, '개', 'veg'], ['대파', 1, '대', 'veg'], ['떡볶이떡', 150, 'g', 'grain'],
      ['고추장', 2, '큰술', 'pantry'], ['고춧가루', 1, '큰술', 'pantry'], ['간장', 2, '큰술', 'pantry'], ['설탕', 1, '큰술', 'pantry'], ['다진마늘', 1, '큰술', 'veg']],
    steps: ['닭다리살을 한입 크기로 썰어 양념에 10분 재운다.', '팬에 양배추, 고구마, 떡을 깔고 닭을 올린다.', '뚜껑 덮고 중불에서 익히다가 뒤적여 볶는다.'],
    kidTip: '아이 몫은 간장·올리고당으로만 따로 볶아주세요.',
  },
  {
    id: 'k-chicken-soy', type: 'main', cuisine: 'korean', name: '닭다리살 간장구이', emoji: '🍗', time: 25, protein: 'chicken',
    ingredients: [['닭다리살', 600, 'g', 'meat'], ['양파', 0.5, '개', 'veg'], ['대파', 0.5, '대', 'veg'],
      ['간장', 3, '큰술', 'pantry'], ['맛술', 2, '큰술', 'pantry'], ['설탕', 1, '큰술', 'pantry'], ['올리고당', 1, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg']],
    steps: ['닭다리살 껍질 쪽부터 팬에 올려 노릇하게 굽는다.', '뒤집어 속까지 익힌 뒤 양파를 넣는다.', '양념을 붓고 윤기 나게 졸인 뒤 썰어 낸다.'],
    kidTip: '데리야끼 맛이라 아이들이 밥을 잘 먹어요.',
  },
  {
    id: 'k-mackerel', type: 'main', cuisine: 'korean', name: '고등어구이', emoji: '🐟', time: 20, protein: 'fish',
    ingredients: [['손질 고등어', 2, '마리', 'sea'], ['식용유', 0, '', 'pantry']],
    steps: ['고등어는 키친타월로 물기를 닦는다.', '기름 두른 팬에 껍질부터 중약불로 굽는다.', '뒤집어 속까지 익힌다. (에어프라이어 200℃ 15분도 OK)'],
    kidTip: '가시를 꼼꼼히 발라주세요. 순살 고등어를 사면 편해요.',
  },
  {
    id: 'k-samchi', type: 'main', cuisine: 'korean', name: '삼치구이', emoji: '🐟', time: 20, protein: 'fish',
    ingredients: [['손질 삼치', 400, 'g', 'sea'], ['밀가루', 2, '큰술', 'pantry'], ['식용유', 0, '', 'pantry']],
    steps: ['삼치에 소금을 살짝 뿌리고 밀가루를 얇게 묻힌다.', '기름 두른 팬에 앞뒤로 노릇하게 굽는다.'],
    kidTip: '살이 부드럽고 가시가 굵어서 아이들 생선으로 좋아요.',
  },
  {
    id: 'k-mackerel-radish', type: 'main', cuisine: 'korean', name: '고등어 무조림', emoji: '🐟', time: 35, protein: 'fish', spicy: true,
    ingredients: [['손질 고등어', 2, '마리', 'sea'], ['무', 300, 'g', 'veg'], ['양파', 0.5, '개', 'veg'], ['대파', 1, '대', 'veg'],
      ['고춧가루', 2, '큰술', 'pantry'], ['간장', 4, '큰술', 'pantry'], ['설탕', 1, '큰술', 'pantry'], ['다진마늘', 1, '큰술', 'veg']],
    steps: ['무를 도톰하게 썰어 냄비에 깔고 물 2컵과 양념 절반을 넣어 10분 끓인다.', '고등어와 나머지 양념을 올리고 15분 졸인다.', '양파·대파를 넣고 5분 더 졸인다.'],
    kidTip: '양념이 밴 무는 물에 살짝 헹궈주면 아이들도 먹을 수 있어요.',
  },
  {
    id: 'k-jangjorim', type: 'main', cuisine: 'korean', name: '소고기 메추리알 장조림', emoji: '🥚', time: 50, protein: 'beef',
    ingredients: [['소고기 장조림용(홍두깨살)', 400, 'g', 'meat'], ['깐 메추리알', 300, 'g', 'egg'], ['대파', 1, '대', 'veg'],
      ['간장', 8, '큰술', 'pantry'], ['설탕', 3, '큰술', 'pantry'], ['다진마늘', 1, '큰술', 'veg']],
    steps: ['소고기를 큼직하게 썰어 대파와 물 5컵에 30분 삶는다.', '간장, 설탕, 다진마늘, 메추리알을 넣고 15분 졸인다.', '고기를 결대로 찢는다.'],
    kidTip: '넉넉히 만들면 일주일 반찬이 돼요.',
  },
  {
    id: 'k-dubu-jorim', type: 'main', cuisine: 'korean', name: '두부조림', emoji: '🧈', time: 20, protein: 'tofu',
    ingredients: [['두부', 2, '모', 'proc'], ['양파', 0.5, '개', 'veg'], ['대파', 0.5, '대', 'veg'],
      ['간장', 4, '큰술', 'pantry'], ['설탕', 1, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg'], ['참기름', 1, '큰술', 'pantry'], ['깨', 0, '', 'pantry']],
    steps: ['두부를 1cm 두께로 썰어 소금 뿌리고 물기를 뺀 뒤 노릇하게 부친다.', '양념장과 물 반 컵을 넣고 양파·대파를 올려 졸인다.', '참기름, 깨로 마무리.'],
    kidTip: '어른은 고춧가루를 따로 뿌려 드세요.',
  },
  {
    id: 'k-sausage', type: 'main', cuisine: 'korean', name: '소시지 야채볶음', emoji: '🌭', time: 15, protein: 'pork',
    ingredients: [['비엔나 소시지', 300, 'g', 'proc'], ['양파', 0.5, '개', 'veg'], ['파프리카', 1, '개', 'veg'],
      ['케첩', 3, '큰술', 'pantry'], ['올리고당', 1, '큰술', 'pantry'], ['간장', 0.5, '큰술', 'pantry']],
    steps: ['소시지에 칼집을 내고 끓는 물에 살짝 데친다.', '채소와 함께 볶다가 케첩, 올리고당, 간장을 넣고 버무린다.'],
    kidTip: '4세는 소시지를 길게 반 갈라서 주세요(목 걸림 방지).',
  },
  {
    id: 'k-eggroll', type: 'main', cuisine: 'korean', name: '야채 계란말이', emoji: '🍳', time: 15, protein: 'egg',
    ingredients: [['계란', 8, '개', 'egg'], ['당근', 0.3, '개', 'veg'], ['대파', 0.3, '대', 'veg'], ['슬라이스 치즈', 2, '장', 'egg'], ['소금', 0, '', 'pantry']],
    steps: ['계란에 잘게 썬 당근·대파와 소금을 넣고 푼다.', '약불 팬에 얇게 부어 반쯤 익으면 치즈를 올리고 돌돌 만다.', '나머지를 부어가며 반복해 두툼하게 만든다.'],
  },
  {
    id: 'k-japchae', type: 'main', cuisine: 'korean', name: '잡채', emoji: '🍜', time: 40, protein: 'beef',
    ingredients: [['당면', 200, 'g', 'grain'], ['소고기 불고기용', 200, 'g', 'meat'], ['시금치', 200, 'g', 'veg'], ['당근', 0.5, '개', 'veg'], ['양파', 1, '개', 'veg'], ['느타리버섯', 150, 'g', 'veg'],
      ['간장', 6, '큰술', 'pantry'], ['설탕', 3, '큰술', 'pantry'], ['참기름', 3, '큰술', 'pantry'], ['다진마늘', 1, '큰술', 'veg']],
    steps: ['당면은 30분 불린 뒤 6분 삶아 간장·설탕·참기름으로 밑간한다.', '시금치는 데치고, 채소와 고기는 각각 볶는다.', '모두 합쳐 간장·설탕·참기름으로 간을 맞추며 버무린다.'],
    kidTip: '당면을 가위로 짧게 잘라주세요.',
  },
  {
    id: 'k-squid', type: 'main', cuisine: 'korean', name: '오징어볶음', emoji: '🦑', time: 25, protein: 'seafood', spicy: true,
    ingredients: [['손질 오징어', 2, '마리', 'sea'], ['양파', 1, '개', 'veg'], ['양배추', 150, 'g', 'veg'], ['대파', 1, '대', 'veg'], ['당근', 0.3, '개', 'veg'],
      ['고추장', 2, '큰술', 'pantry'], ['고춧가루', 1, '큰술', 'pantry'], ['간장', 2, '큰술', 'pantry'], ['설탕', 1, '큰술', 'pantry'], ['다진마늘', 1, '큰술', 'veg']],
    steps: ['오징어는 칼집 내어 한입 크기로 썬다.', '센불에 채소를 먼저 볶고 오징어와 양념을 넣는다.', '오징어가 하얗게 익으면 바로 불을 끈다(오래 볶으면 질겨져요).'],
    kidTip: '양념 전에 오징어·채소 일부를 덜어 버터간장으로 볶아주세요.',
  },
  {
    id: 'k-suyuk', type: 'main', cuisine: 'korean', name: '돼지고기 수육', emoji: '🥬', time: 60, protein: 'pork',
    ingredients: [['돼지고기 앞다리(수육용)', 800, 'g', 'meat'], ['양파', 1, '개', 'veg'], ['대파', 1, '대', 'veg'], ['통마늘', 10, '개', 'veg'], ['쌈채소', 1, '봉', 'veg'],
      ['된장', 1, '큰술', 'pantry'], ['새우젓', 0, '', 'pantry']],
    steps: ['냄비에 물, 된장, 양파, 대파, 통마늘을 넣고 끓인다.', '고기를 넣고 중불에서 45~50분 삶는다.', '한 김 식혀 얇게 썰어 쌈채소, 새우젓과 낸다.'],
    kidTip: '살코기 부분을 얇게 썰어 주면 아이들도 잘 먹어요.',
  },
  {
    id: 'k-beef-mushroom', type: 'main', cuisine: 'korean', name: '소고기 버섯볶음', emoji: '🍄', time: 20, protein: 'beef',
    ingredients: [['소고기 불고기용', 400, 'g', 'meat'], ['느타리버섯', 200, 'g', 'veg'], ['양파', 1, '개', 'veg'], ['대파', 0.5, '대', 'veg'],
      ['간장', 3, '큰술', 'pantry'], ['설탕', 1, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg'], ['참기름', 1, '큰술', 'pantry']],
    steps: ['고기에 간장, 설탕, 다진마늘로 밑간한다.', '고기를 볶다가 버섯, 양파를 넣고 센불에 볶는다.', '대파, 참기름으로 마무리.'],
  },
  {
    id: 'k-donggeurang', type: 'main', cuisine: 'korean', name: '동그랑땡', emoji: '🟡', time: 40, protein: 'pork',
    ingredients: [['다진 돼지고기', 400, 'g', 'meat'], ['두부', 0.5, '모', 'proc'], ['당근', 0.3, '개', 'veg'], ['양파', 0.5, '개', 'veg'], ['대파', 0.5, '대', 'veg'], ['계란', 3, '개', 'egg'],
      ['밀가루', 3, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg'], ['소금', 0, '', 'pantry'], ['후추', 0, '', 'pantry']],
    steps: ['두부는 물기를 꼭 짜고, 채소는 잘게 다진다.', '고기, 두부, 채소, 다진마늘, 소금, 후추를 치대어 동글납작하게 빚는다.', '밀가루 → 계란물을 입혀 약불에 노릇하게 부친다.'],
    kidTip: '넉넉히 빚어 냉동해두면 바쁜 날 반찬으로 최고예요.',
  },
  {
    id: 'k-salmon', type: 'main', cuisine: 'korean', name: '연어 데리야끼 구이', emoji: '🍣', time: 20, protein: 'fish',
    ingredients: [['생연어', 400, 'g', 'sea'], ['양파', 0.5, '개', 'veg'],
      ['간장', 3, '큰술', 'pantry'], ['맛술', 2, '큰술', 'pantry'], ['설탕', 1, '큰술', 'pantry'], ['올리고당', 1, '큰술', 'pantry']],
    steps: ['연어를 한입 크기로 썰어 팬에 앞뒤로 굽는다.', '양파를 넣고 간장, 맛술, 설탕, 올리고당을 부어 졸인다.'],
    kidTip: '가시가 거의 없어 아이 생선 반찬으로 좋아요.',
  },
  {
    id: 'k-tteokgalbi', type: 'main', cuisine: 'korean', name: '떡갈비', emoji: '🍖', time: 35, protein: 'beef',
    ingredients: [['다진 소고기', 300, 'g', 'meat'], ['다진 돼지고기', 200, 'g', 'meat'], ['양파', 0.5, '개', 'veg'], ['대파', 0.5, '대', 'veg'],
      ['간장', 3, '큰술', 'pantry'], ['설탕', 1.5, '큰술', 'pantry'], ['다진마늘', 1, '큰술', 'veg'], ['참기름', 1, '큰술', 'pantry'], ['빵가루', 15, 'g', 'grain']],
    steps: ['다진 양파·대파와 고기, 양념, 빵가루를 넣고 끈기 있게 치댄다.', '납작하게 빚어 약불에 뚜껑 덮고 앞뒤로 굽는다.', '남은 양념을 발라 윤기 나게 마무리.'],
  },
  {
    id: 'k-galbijjim', type: 'main', cuisine: 'korean', name: '소갈비찜', emoji: '🍖', time: 90, protein: 'beef',
    ingredients: [['소갈비(찜용)', 1200, 'g', 'meat'], ['무', 300, 'g', 'veg'], ['당근', 1, '개', 'veg'], ['대파', 1, '대', 'veg'], ['배', 0.5, '개', 'fruit'],
      ['간장', 8, '큰술', 'pantry'], ['설탕', 3, '큰술', 'pantry'], ['다진마늘', 2, '큰술', 'veg']],
    steps: ['갈비는 찬물에 1시간 핏물을 빼고 한 번 데쳐 헹군다.', '간 배, 간장, 설탕, 다진마늘 양념과 물 4컵을 넣고 40분 끓인다.', '무·당근을 넣고 30분 더 졸인다.'],
    kidTip: '주말 특식! 뼈 붙은 부분은 살만 발라주세요.',
  },
  {
    id: 'k-pork-galbijjim', type: 'main', cuisine: 'korean', name: '돼지갈비찜', emoji: '🍖', time: 70, protein: 'pork',
    ingredients: [['돼지갈비(찜용)', 1200, 'g', 'meat'], ['감자', 2, '개', 'veg'], ['당근', 1, '개', 'veg'], ['양파', 1, '개', 'veg'], ['대파', 1, '대', 'veg'],
      ['간장', 7, '큰술', 'pantry'], ['설탕', 3, '큰술', 'pantry'], ['올리고당', 1, '큰술', 'pantry'], ['다진마늘', 2, '큰술', 'veg']],
    steps: ['갈비는 핏물을 빼고 한 번 데쳐 헹군다.', '양념과 물 4컵을 넣고 30분 끓인다.', '감자·당근·양파를 넣고 20분, 대파·올리고당을 넣고 5분 더 졸인다.'],
  },
  {
    id: 'k-daepae', type: 'main', cuisine: 'korean', name: '대패삼겹 숙주볶음', emoji: '🥓', time: 15, protein: 'pork',
    ingredients: [['대패삼겹살', 400, 'g', 'meat'], ['숙주', 300, 'g', 'veg'], ['대파', 1, '대', 'veg'],
      ['간장', 2, '큰술', 'pantry'], ['굴소스', 1, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg']],
    steps: ['대패삼겹을 바삭하게 볶고 기름을 조금 덜어낸다.', '대파, 다진마늘을 넣고 향을 낸다.', '숙주와 간장·굴소스를 넣고 센불에 30초만 볶는다.'],
  },
  {
    id: 'k-pajeon', type: 'main', cuisine: 'korean', name: '해물파전', emoji: '🥞', time: 30, protein: 'seafood',
    ingredients: [['부침가루', 300, 'g', 'grain'], ['쪽파', 200, 'g', 'veg'], ['냉동 해물믹스', 200, 'g', 'sea'], ['계란', 1, '개', 'egg'], ['식용유', 0, '', 'pantry']],
    steps: ['부침가루에 찬물을 넣어 묽게 반죽한다.', '팬에 기름 넉넉히 두르고 쪽파를 깔고 반죽, 해물을 올린다.', '계란물을 두르고 뒤집어 바삭하게 굽는다.'],
    kidTip: '아이 몫은 손바닥 크기로 작게 부쳐주세요.',
  },
  {
    id: 'k-dubu-buchim', type: 'main', cuisine: 'korean', name: '두부부침 & 양념장', emoji: '🧈', time: 15, protein: 'tofu',
    ingredients: [['두부', 2, '모', 'proc'], ['계란', 2, '개', 'egg'], ['대파', 0.3, '대', 'veg'], ['간장', 2, '큰술', 'pantry'], ['깨', 0, '', 'pantry'], ['소금', 0, '', 'pantry']],
    steps: ['두부를 썰어 소금을 뿌리고 물기를 뺀다.', '계란물을 입혀 노릇하게 부친다.', '간장, 송송 썬 대파, 깨로 양념장을 만든다.'],
  },
  // 한 그릇 요리
  {
    id: 'k-bibimbap', type: 'main', cuisine: 'korean', name: '소고기 비빔밥', emoji: '🥗', time: 35, protein: 'beef', oneDish: true,
    ingredients: [['다진 소고기', 200, 'g', 'meat'], ['시금치', 200, 'g', 'veg'], ['콩나물', 200, 'g', 'veg'], ['당근', 0.5, '개', 'veg'], ['애호박', 0.5, '개', 'veg'], ['계란', 4, '개', 'egg'],
      ['밥', 4, '공기', 'pantry'], ['간장', 2, '큰술', 'pantry'], ['참기름', 2, '큰술', 'pantry'], ['고추장', 0, '', 'pantry']],
    steps: ['시금치·콩나물은 데쳐 소금·참기름에 무친다.', '당근·애호박은 채 썰어 볶고, 소고기는 간장·설탕에 볶는다.', '밥 위에 나물, 고기, 계란 프라이를 올린다.'],
    kidTip: '아이들은 고추장 대신 간장+참기름으로 비벼주세요.',
  },
  {
    id: 'k-kimchi-fried', type: 'main', cuisine: 'korean', name: '김치볶음밥', emoji: '🍳', time: 20, protein: 'pork', oneDish: true, spicy: true,
    ingredients: [['김치', 300, 'g', 'pantry'], ['햄(통햄)', 200, 'g', 'proc'], ['대파', 0.5, '대', 'veg'], ['계란', 4, '개', 'egg'], ['밥', 4, '공기', 'pantry'], ['설탕', 0.5, '큰술', 'pantry'], ['참기름', 1, '큰술', 'pantry']],
    steps: ['대파를 기름에 볶아 파기름을 낸다.', '햄, 잘게 썬 김치, 설탕을 넣고 볶다가 밥을 넣어 볶는다.', '계란 프라이를 올린다.'],
    kidTip: '아이 몫은 김치를 물에 헹궈 볶거나 햄볶음밥으로 따로.',
  },
  {
    id: 'k-shrimp-fried', type: 'main', cuisine: 'korean', name: '새우볶음밥', emoji: '🍤', time: 20, protein: 'seafood', oneDish: true,
    ingredients: [['냉동 칵테일새우', 300, 'g', 'sea'], ['계란', 3, '개', 'egg'], ['당근', 0.3, '개', 'veg'], ['양파', 0.5, '개', 'veg'], ['대파', 0.5, '대', 'veg'], ['밥', 4, '공기', 'pantry'],
      ['굴소스', 1, '큰술', 'pantry'], ['버터', 10, 'g', 'egg']],
    steps: ['파기름에 잘게 썬 채소와 새우를 볶는다.', '한쪽에 계란을 스크램블하고 밥을 넣어 볶는다.', '굴소스, 버터로 간한다.'],
  },
  {
    id: 'k-curry', type: 'main', cuisine: 'korean', name: '카레라이스', emoji: '🍛', time: 35, protein: 'pork', oneDish: true,
    ingredients: [['돼지고기 카레용', 300, 'g', 'meat'], ['감자', 2, '개', 'veg'], ['당근', 1, '개', 'veg'], ['양파', 1, '개', 'veg'], ['카레가루', 100, 'g', 'proc'], ['밥', 4, '공기', 'pantry']],
    steps: ['고기와 깍둑 썬 채소를 볶는다.', '물 4컵을 넣고 채소가 익을 때까지 15분 끓인다.', '불을 줄이고 카레가루를 풀어 저어가며 5분 더 끓인다.'],
    kidTip: '순한맛 카레를 쓰고 넉넉히 만들어 다음날 아침에도 OK.',
  },
  {
    id: 'k-gyudon', type: 'main', cuisine: 'korean', name: '소고기 덮밥', emoji: '🍚', time: 20, protein: 'beef', oneDish: true,
    ingredients: [['소고기 불고기용', 400, 'g', 'meat'], ['양파', 1, '개', 'veg'], ['대파', 0.5, '대', 'veg'], ['밥', 4, '공기', 'pantry'],
      ['간장', 4, '큰술', 'pantry'], ['설탕', 1.5, '큰술', 'pantry'], ['맛술', 2, '큰술', 'pantry']],
    steps: ['물 1컵, 간장, 설탕, 맛술을 끓이고 채 썬 양파를 넣는다.', '양파가 투명해지면 고기를 넣어 익힌다.', '밥 위에 국물째 올리고 대파를 뿌린다.'],
  },
  {
    id: 'k-janchi', type: 'main', cuisine: 'korean', name: '잔치국수', emoji: '🍜', time: 25, protein: 'egg', oneDish: true,
    ingredients: [['소면', 400, 'g', 'grain'], ['애호박', 0.5, '개', 'veg'], ['당근', 0.3, '개', 'veg'], ['계란', 2, '개', 'egg'], ['조미김', 1, '봉', 'sea'],
      ['코인육수', 2, '개', 'pantry'], ['국간장', 2, '큰술', 'pantry']],
    steps: ['물 6컵에 코인육수를 넣고 끓여 국간장, 소금으로 간한다.', '애호박·당근은 채 썰어 볶고 계란은 지단을 부친다.', '소면을 삶아 찬물에 헹구고 육수를 부어 고명을 올린다.'],
    kidTip: '면을 가위로 짧게 잘라주세요.',
  },
  {
    id: 'k-kalguksu', type: 'main', cuisine: 'korean', name: '닭칼국수', emoji: '🍜', time: 40, protein: 'chicken', oneDish: true,
    ingredients: [['칼국수면', 600, 'g', 'grain'], ['닭다리살', 400, 'g', 'meat'], ['애호박', 0.5, '개', 'veg'], ['감자', 1, '개', 'veg'], ['양파', 0.5, '개', 'veg'], ['대파', 0.5, '대', 'veg'],
      ['다진마늘', 1, '큰술', 'veg'], ['국간장', 2, '큰술', 'pantry']],
    steps: ['닭다리살을 물 8컵에 대파, 다진마늘과 20분 삶아 육수를 낸다.', '닭은 건져 찢고, 감자·양파를 넣어 5분 끓인다.', '면과 애호박을 넣고 5분, 국간장·소금으로 간하고 닭을 올린다.'],
  },
  {
    id: 'k-tteokguk', type: 'main', cuisine: 'korean', name: '소고기 떡국', emoji: '🥣', time: 25, protein: 'beef', oneDish: true,
    ingredients: [['떡국떡', 600, 'g', 'grain'], ['소고기 국거리', 200, 'g', 'meat'], ['계란', 2, '개', 'egg'], ['대파', 0.5, '대', 'veg'], ['조미김', 1, '봉', 'sea'],
      ['국간장', 2, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg'], ['참기름', 1, '큰술', 'pantry']],
    steps: ['떡은 물에 10분 불린다.', '참기름에 소고기를 볶다가 물 7컵을 넣고 10분 끓인다.', '떡을 넣고 떠오르면 국간장으로 간하고 계란물, 대파를 넣는다.'],
    kidTip: '떡을 작게 잘라 식혀서 주세요.',
  },
  {
    id: 'k-mandutguk', type: 'main', cuisine: 'korean', name: '떡만둣국', emoji: '🥟', time: 20, protein: 'pork', oneDish: true,
    ingredients: [['냉동 만두', 500, 'g', 'proc'], ['떡국떡', 300, 'g', 'grain'], ['계란', 2, '개', 'egg'], ['대파', 0.5, '대', 'veg'], ['조미김', 1, '봉', 'sea'],
      ['코인육수', 2, '개', 'pantry'], ['국간장', 2, '큰술', 'pantry']],
    steps: ['물 7컵에 코인육수를 넣어 끓인다.', '만두와 불린 떡을 넣고 떠오를 때까지 끓인다.', '국간장으로 간하고 계란물과 대파를 넣는다.'],
  },

  // ─────────────────────────── 국·찌개 ───────────────────────────
  {
    id: 's-doenjang', type: 'soup', cuisine: 'korean', name: '된장찌개', emoji: '🍲', time: 25, protein: 'tofu',
    ingredients: [['두부', 0.5, '모', 'proc'], ['애호박', 0.5, '개', 'veg'], ['감자', 1, '개', 'veg'], ['양파', 0.5, '개', 'veg'], ['대파', 0.3, '대', 'veg'],
      ['된장', 2.5, '큰술', 'pantry'], ['코인육수', 1, '개', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg']],
    steps: ['물 3컵에 코인육수와 된장을 풀어 끓인다.', '감자를 먼저 넣고 5분, 애호박·양파·두부를 넣고 5분 더 끓인다.', '다진마늘, 대파를 넣고 마무리.'],
    kidTip: '청양고추는 어른 그릇에만 따로 넣어주세요.',
  },
  {
    id: 's-kimchi', type: 'soup', cuisine: 'korean', name: '김치찌개', emoji: '🍲', time: 30, protein: 'pork', spicy: true,
    ingredients: [['김치', 300, 'g', 'pantry'], ['돼지고기 찌개용', 200, 'g', 'meat'], ['두부', 0.5, '모', 'proc'], ['양파', 0.5, '개', 'veg'], ['대파', 0.5, '대', 'veg'],
      ['고춧가루', 0.5, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg']],
    steps: ['고기와 김치를 냄비에서 충분히 볶는다.', '물 3컵을 넣고 15분 끓인다.', '두부, 양파, 대파, 고춧가루, 다진마늘을 넣고 5분 더 끓인다.'],
    kidTip: '아이들은 고기와 두부만 건져서 물에 살짝 헹궈 주세요.',
  },
  {
    id: 's-miyeok', type: 'soup', cuisine: 'korean', name: '소고기 미역국', emoji: '🥣', time: 30, protein: 'beef',
    ingredients: [['마른미역', 20, 'g', 'sea'], ['소고기 국거리', 200, 'g', 'meat'], ['국간장', 2, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg'], ['참기름', 1, '큰술', 'pantry']],
    steps: ['미역은 물에 15분 불려 먹기 좋게 자른다.', '참기름에 소고기와 미역을 볶는다.', '물 6컵, 국간장, 다진마늘을 넣고 20분 끓인다.'],
    kidTip: '아이들이 가장 잘 먹는 국 중 하나! 미역을 짧게 잘라주세요.',
  },
  {
    id: 's-mu', type: 'soup', cuisine: 'korean', name: '소고기 무국', emoji: '🥣', time: 30, protein: 'beef',
    ingredients: [['무', 300, 'g', 'veg'], ['소고기 국거리', 200, 'g', 'meat'], ['대파', 0.5, '대', 'veg'], ['국간장', 2, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg'], ['참기름', 1, '큰술', 'pantry']],
    steps: ['무는 나박썰기 한다.', '참기름에 소고기와 무를 볶는다.', '물 6컵을 넣고 20분 끓인 뒤 국간장, 다진마늘, 대파를 넣는다.'],
  },
  {
    id: 's-egg', type: 'soup', cuisine: 'korean', name: '계란국', emoji: '🥚', time: 10, protein: 'egg',
    ingredients: [['계란', 3, '개', 'egg'], ['대파', 0.3, '대', 'veg'], ['코인육수', 1, '개', 'pantry'], ['국간장', 1, '큰술', 'pantry']],
    steps: ['물 4컵에 코인육수를 넣고 끓인다.', '국간장, 소금으로 간하고 풀어둔 계란을 둘러 붓는다.', '대파를 넣고 바로 불을 끈다.'],
  },
  {
    id: 's-kongnamul', type: 'soup', cuisine: 'korean', name: '콩나물국', emoji: '🌱', time: 15, protein: 'veg',
    ingredients: [['콩나물', 300, 'g', 'veg'], ['대파', 0.3, '대', 'veg'], ['코인육수', 1, '개', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg'], ['국간장', 1, '큰술', 'pantry']],
    steps: ['물 5컵에 코인육수와 콩나물을 넣고 뚜껑 덮어 5분 끓인다.', '다진마늘, 국간장, 소금으로 간한다.', '대파를 넣고 1분 더 끓인다.'],
  },
  {
    id: 's-bugeo', type: 'soup', cuisine: 'korean', name: '북엇국', emoji: '🐟', time: 20, protein: 'fish',
    ingredients: [['북어채', 40, 'g', 'sea'], ['무', 100, 'g', 'veg'], ['계란', 2, '개', 'egg'], ['대파', 0.3, '대', 'veg'],
      ['참기름', 1, '큰술', 'pantry'], ['국간장', 1, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg']],
    steps: ['북어채는 물에 살짝 적셔 참기름에 무와 함께 볶는다.', '물 5컵을 넣고 10분 끓여 국간장으로 간한다.', '계란물을 두르고 대파를 넣는다.'],
  },
  {
    id: 's-eomuk', type: 'soup', cuisine: 'korean', name: '어묵국', emoji: '🍢', time: 20, protein: 'fish',
    ingredients: [['사각어묵', 200, 'g', 'proc'], ['무', 150, 'g', 'veg'], ['대파', 0.3, '대', 'veg'], ['코인육수', 2, '개', 'pantry'], ['국간장', 1, '큰술', 'pantry']],
    steps: ['물 6컵에 코인육수와 무를 넣고 10분 끓인다.', '어묵을 썰어 넣고 5분 끓인다.', '국간장으로 간하고 대파를 넣는다.'],
  },
  {
    id: 's-gamja', type: 'soup', cuisine: 'korean', name: '감자국', emoji: '🥔', time: 20, protein: 'veg',
    ingredients: [['감자', 2, '개', 'veg'], ['양파', 0.5, '개', 'veg'], ['대파', 0.3, '대', 'veg'], ['코인육수', 1, '개', 'pantry'], ['국간장', 1, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg']],
    steps: ['감자는 반달 모양으로 썬다.', '물 5컵에 코인육수, 감자를 넣고 10분 끓인다.', '양파, 다진마늘, 국간장을 넣고 5분 뒤 대파로 마무리.'],
  },
  {
    id: 's-sundubu', type: 'soup', cuisine: 'korean', name: '순두부찌개', emoji: '🍲', time: 20, protein: 'tofu', spicy: true,
    ingredients: [['순두부', 1, '봉', 'proc'], ['다진 돼지고기', 100, 'g', 'meat'], ['계란', 2, '개', 'egg'], ['양파', 0.5, '개', 'veg'], ['대파', 0.3, '대', 'veg'],
      ['고춧가루', 1, '큰술', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg'], ['국간장', 1, '큰술', 'pantry']],
    steps: ['기름에 대파, 다진 돼지고기를 볶다가 고춧가루를 넣어 고추기름을 낸다.', '물 2컵, 양파를 넣고 끓인다.', '순두부를 크게 떠 넣고 국간장으로 간한 뒤 계란을 올린다.'],
    kidTip: '고춧가루 넣기 전 순두부 일부를 덜어 맑은 순두부국으로 주세요.',
  },
  {
    id: 's-sigeumchi', type: 'soup', cuisine: 'korean', name: '시금치 된장국', emoji: '🥬', time: 15, protein: 'veg',
    ingredients: [['시금치', 150, 'g', 'veg'], ['대파', 0.3, '대', 'veg'], ['된장', 1.5, '큰술', 'pantry'], ['코인육수', 1, '개', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg']],
    steps: ['물 5컵에 코인육수와 된장을 풀어 끓인다.', '손질한 시금치를 넣고 3분 끓인다.', '다진마늘, 대파를 넣고 마무리.'],
  },
  {
    id: 's-baechu', type: 'soup', cuisine: 'korean', name: '배추 된장국', emoji: '🥬', time: 20, protein: 'veg',
    ingredients: [['알배추', 0.5, '통', 'veg'], ['대파', 0.3, '대', 'veg'], ['된장', 1.5, '큰술', 'pantry'], ['코인육수', 1, '개', 'pantry'], ['다진마늘', 0.5, '큰술', 'veg']],
    steps: ['물 5컵에 코인육수와 된장을 풀어 끓인다.', '먹기 좋게 썬 배추를 넣고 10분 끓인다.', '다진마늘, 대파를 넣고 마무리.'],
    kidTip: '배추가 달큰해서 아이들이 잘 먹어요.',
  },

  // ─────────────────────────── 양식 저녁 ───────────────────────────
  {
    id: 'w-tomato-pasta', type: 'western', cuisine: 'western', name: '미트소스 스파게티', emoji: '🍝', time: 30, protein: 'beef',
    ingredients: [['스파게티면', 400, 'g', 'grain'], ['다진 소고기', 300, 'g', 'meat'], ['양파', 1, '개', 'veg'], ['토마토 파스타소스', 600, 'g', 'proc'], ['파마산 치즈가루', 0, '', 'egg'], ['올리브유', 0, '', 'pantry']],
    steps: ['다진 양파와 소고기를 올리브유에 볶는다.', '파스타소스를 넣고 10분 뭉근히 끓인다.', '삶은 면과 버무리고 치즈가루를 뿌린다.'],
    kidTip: '면을 짧게 잘라주거나 펜네로 바꾸면 흘리지 않고 잘 먹어요.',
  },
  {
    id: 'w-cream-pasta', type: 'western', cuisine: 'western', name: '베이컨 크림 파스타', emoji: '🍝', time: 25, protein: 'pork',
    ingredients: [['스파게티면', 400, 'g', 'grain'], ['베이컨', 150, 'g', 'proc'], ['양송이버섯', 150, 'g', 'veg'], ['양파', 0.5, '개', 'veg'], ['생크림', 250, 'ml', 'egg'], ['우유', 250, 'ml', 'egg'],
      ['파마산 치즈가루', 0, '', 'egg'], ['다진마늘', 0.5, '큰술', 'veg']],
    steps: ['베이컨, 다진마늘, 양파, 버섯을 볶는다.', '생크림과 우유를 붓고 약불에서 살짝 졸인다.', '삶은 면을 넣고 버무려 소금·치즈가루로 간한다.'],
  },
  {
    id: 'w-rose-shrimp', type: 'western', cuisine: 'western', name: '새우 로제 펜네', emoji: '🍤', time: 25, protein: 'seafood',
    ingredients: [['펜네', 400, 'g', 'grain'], ['냉동 칵테일새우', 250, 'g', 'sea'], ['토마토 파스타소스', 400, 'g', 'proc'], ['생크림', 200, 'ml', 'egg'], ['양파', 0.5, '개', 'veg'], ['다진마늘', 0.5, '큰술', 'veg']],
    steps: ['다진마늘, 양파, 새우를 볶는다.', '토마토소스와 생크림을 넣고 5분 끓인다.', '삶은 펜네를 넣고 버무린다.'],
    kidTip: '펜네는 포크로 찍기 쉬워서 아이들이 혼자 잘 먹어요.',
  },
  {
    id: 'w-hamburg', type: 'western', cuisine: 'western', name: '함박스테이크', emoji: '🍔', time: 40, protein: 'beef',
    ingredients: [['다진 소고기', 300, 'g', 'meat'], ['다진 돼지고기', 200, 'g', 'meat'], ['양파', 1, '개', 'veg'], ['계란', 1, '개', 'egg'], ['우유', 50, 'ml', 'egg'], ['브로콜리', 1, '송이', 'veg'], ['버터', 10, 'g', 'egg'],
      ['빵가루', 20, 'g', 'grain'], ['돈까스소스', 4, '큰술', 'pantry'], ['케첩', 2, '큰술', 'pantry']],
    steps: ['다진 양파를 볶아 식힌 뒤 고기, 계란, 우유 적신 빵가루와 치댄다.', '도톰하게 빚어 가운데를 눌러주고 약불에 뚜껑 덮어 굽는다.', '팬에 돈까스소스, 케첩, 버터, 물을 끓여 소스를 만들어 붓는다.'],
    kidTip: '데친 브로콜리와 계란 프라이를 곁들이면 아이들 최애 메뉴.',
  },
  {
    id: 'w-donkatsu', type: 'western', cuisine: 'western', name: '돈까스 & 양배추샐러드', emoji: '🍖', time: 30, protein: 'pork',
    ingredients: [['돼지고기 등심(돈까스용)', 500, 'g', 'meat'], ['계란', 2, '개', 'egg'], ['빵가루', 200, 'g', 'grain'], ['양배추', 200, 'g', 'veg'],
      ['밀가루', 4, '큰술', 'pantry'], ['돈까스소스', 0, '', 'pantry'], ['식용유', 0, '', 'pantry']],
    steps: ['고기를 두드려 펴고 소금·후추를 뿌린다.', '밀가루 → 계란물 → 빵가루 순서로 옷을 입힌다.', '170℃ 기름에 노릇하게 튀기거나 에어프라이어 200℃ 15분.'],
    kidTip: '바쁜 날엔 시판 냉동 돈까스로 10분 컷.',
  },
  {
    id: 'w-omurice', type: 'western', cuisine: 'western', name: '오므라이스', emoji: '🍳', time: 30, protein: 'egg',
    ingredients: [['계란', 6, '개', 'egg'], ['햄(통햄)', 100, 'g', 'proc'], ['양파', 0.5, '개', 'veg'], ['당근', 0.3, '개', 'veg'], ['파프리카', 0.5, '개', 'veg'], ['밥', 4, '공기', 'pantry'],
      ['케첩', 4, '큰술', 'pantry'], ['버터', 10, 'g', 'egg']],
    steps: ['잘게 썬 햄과 채소를 버터에 볶고 밥, 케첩을 넣어 볶는다.', '계란을 풀어 얇게 부친다.', '볶음밥을 계란으로 감싸고 케첩을 뿌린다.'],
    kidTip: '케첩으로 아이 이름이나 그림을 그려주세요.',
  },
  {
    id: 'w-hayashi', type: 'western', cuisine: 'western', name: '하이라이스', emoji: '🍛', time: 35, protein: 'beef',
    ingredients: [['소고기 불고기용', 300, 'g', 'meat'], ['양파', 2, '개', 'veg'], ['양송이버섯', 150, 'g', 'veg'], ['하이라이스 가루', 100, 'g', 'proc'], ['버터', 10, 'g', 'egg'], ['밥', 4, '공기', 'pantry']],
    steps: ['버터에 채 썬 양파를 갈색이 될 때까지 충분히 볶는다.', '고기와 버섯을 넣고 볶다가 물 3컵을 넣어 끓인다.', '하이라이스 가루를 풀어 저어가며 5분 끓인다.'],
  },
  {
    id: 'w-risotto', type: 'western', cuisine: 'western', name: '닭고기 버섯 크림리조또', emoji: '🍚', time: 35, protein: 'chicken',
    ingredients: [['닭가슴살', 200, 'g', 'meat'], ['양송이버섯', 150, 'g', 'veg'], ['양파', 0.5, '개', 'veg'], ['생크림', 200, 'ml', 'egg'], ['우유', 300, 'ml', 'egg'], ['파마산 치즈가루', 0, '', 'egg'], ['버터', 15, 'g', 'egg'], ['밥', 3, '공기', 'pantry']],
    steps: ['버터에 양파, 닭가슴살, 버섯을 볶는다.', '밥과 우유, 생크림을 넣고 저어가며 걸쭉해질 때까지 끓인다.', '치즈가루, 소금으로 간한다.'],
  },
  {
    id: 'w-tender', type: 'western', cuisine: 'western', name: '치킨텐더 & 감자구이', emoji: '🍗', time: 35, protein: 'chicken',
    ingredients: [['닭안심', 500, 'g', 'meat'], ['감자', 3, '개', 'veg'], ['빵가루', 100, 'g', 'grain'], ['계란', 2, '개', 'egg'],
      ['밀가루', 3, '큰술', 'pantry'], ['올리브유', 0, '', 'pantry'], ['케첩', 0, '', 'pantry']],
    steps: ['감자는 웨지 모양으로 썰어 올리브유, 소금에 버무린다.', '닭안심은 밀가루 → 계란 → 빵가루 순으로 입힌다.', '에어프라이어 200℃에 감자 20분, 텐더 12분 굽는다.'],
    kidTip: '손으로 집어 먹는 메뉴라 아이들이 정말 좋아해요.',
  },
  {
    id: 'w-tortilla-pizza', type: 'western', cuisine: 'western', name: '또띠아 피자', emoji: '🍕', time: 20, protein: 'pork',
    ingredients: [['또띠아', 4, '장', 'grain'], ['토마토 파스타소스', 150, 'g', 'proc'], ['피자치즈', 300, 'g', 'egg'], ['햄(통햄)', 100, 'g', 'proc'], ['양송이버섯', 100, 'g', 'veg'], ['파프리카', 0.5, '개', 'veg'], ['옥수수콘', 1, '캔', 'proc']],
    steps: ['또띠아에 파스타소스를 얇게 바른다.', '햄, 버섯, 파프리카, 옥수수콘, 피자치즈를 올린다.', '에어프라이어 180℃ 8~10분 굽는다.'],
    kidTip: '아이들이 직접 토핑을 올리게 하면 채소도 잘 먹어요.',
  },
  {
    id: 'w-steak', type: 'western', cuisine: 'western', name: '부채살 스테이크 & 구운채소', emoji: '🥩', time: 30, protein: 'beef',
    ingredients: [['소고기 스테이크용(부채살)', 600, 'g', 'meat'], ['감자', 2, '개', 'veg'], ['브로콜리', 1, '송이', 'veg'], ['양파', 1, '개', 'veg'], ['버터', 20, 'g', 'egg'], ['올리브유', 0, '', 'pantry']],
    steps: ['고기는 실온에 30분 두고 소금·후추·올리브유를 바른다.', '센불에 앞뒤로 굽고 버터를 넣어 끼얹어준 뒤 5분 쉬게 한다.', '감자, 브로콜리, 양파를 같은 팬에 굽는다.'],
    kidTip: '아이 몫은 웰던으로 익혀 잘게 썰어주세요.',
  },
  {
    id: 'w-fish-katsu', type: 'western', cuisine: 'western', name: '생선까스', emoji: '🐟', time: 25, protein: 'fish',
    ingredients: [['순살 동태', 400, 'g', 'sea'], ['계란', 2, '개', 'egg'], ['빵가루', 100, 'g', 'grain'], ['양배추', 150, 'g', 'veg'],
      ['밀가루', 3, '큰술', 'pantry'], ['마요네즈', 3, '큰술', 'pantry'], ['식용유', 0, '', 'pantry']],
    steps: ['생선에 소금·후추를 뿌리고 밀가루 → 계란 → 빵가루를 입힌다.', '170℃ 기름에 튀기거나 에어프라이어 200℃ 12분.', '마요네즈에 다진 양파·피클을 섞어 타르타르소스를 만든다.'],
    kidTip: '시판 냉동 생선까스를 쓰면 훨씬 간단해요.',
  },
  {
    id: 'w-mac-cheese', type: 'western', cuisine: 'western', name: '마카로니 그라탕', emoji: '🧀', time: 35, protein: 'pork',
    ingredients: [['마카로니', 300, 'g', 'grain'], ['베이컨', 100, 'g', 'proc'], ['양파', 0.5, '개', 'veg'], ['브로콜리', 0.5, '송이', 'veg'], ['우유', 400, 'ml', 'egg'], ['버터', 20, 'g', 'egg'], ['피자치즈', 200, 'g', 'egg'],
      ['밀가루', 2, '큰술', 'pantry']],
    steps: ['마카로니를 삶고, 베이컨·양파·브로콜리를 볶는다.', '버터에 밀가루를 볶다가 우유를 조금씩 넣어 화이트소스를 만든다.', '모두 섞어 그릇에 담고 치즈를 올려 에어프라이어 180℃ 10분.'],
  },
  {
    id: 'w-chicken-steak', type: 'western', cuisine: 'western', name: '갈릭버터 닭다리살 스테이크', emoji: '🍗', time: 30, protein: 'chicken',
    ingredients: [['닭다리살', 700, 'g', 'meat'], ['감자', 2, '개', 'veg'], ['양파', 0.5, '개', 'veg'], ['버터', 15, 'g', 'egg'],
      ['간장', 2, '큰술', 'pantry'], ['올리고당', 1, '큰술', 'pantry'], ['다진마늘', 1, '큰술', 'veg']],
    steps: ['닭다리살에 소금·후추를 뿌리고 껍질 쪽부터 바삭하게 굽는다.', '감자와 양파를 옆에서 함께 굽는다.', '버터, 다진마늘, 간장, 올리고당을 넣어 소스를 끼얹는다.'],
  },
  {
    id: 'w-beef-stew', type: 'western', cuisine: 'western', name: '토마토 비프스튜', emoji: '🍲', time: 70, protein: 'beef',
    ingredients: [['소고기 국거리', 500, 'g', 'meat'], ['감자', 2, '개', 'veg'], ['당근', 1, '개', 'veg'], ['양파', 1, '개', 'veg'], ['토마토 파스타소스', 300, 'g', 'proc'], ['버터', 15, 'g', 'egg'], ['식빵', 4, '장', 'grain'],
      ['밀가루', 1, '큰술', 'pantry']],
    steps: ['소고기에 밀가루를 묻혀 버터에 겉면을 굽는다.', '채소를 넣어 볶고 파스타소스와 물 4컵을 넣는다.', '약불에서 50분 뭉근히 끓이고 구운 빵을 곁들인다.'],
    kidTip: '주말에 넉넉히 끓여두면 다음날 덮밥으로도 좋아요.',
  },
  // ─────────────────────────── 밀키트 ───────────────────────────
  // 4인 가족(어른2+유아2) 기준 3~4인분 1팩. 2인분 제품이면 2팩.
  {
    id: 'kit-millefeuille', type: 'main', cuisine: 'korean', kit: true, name: '밀푀유나베 밀키트', emoji: '🍲', time: 20, protein: 'beef', oneDish: true,
    ingredients: [['밀푀유나베 밀키트', 1, '팩', 'kit'], ['칼국수면', 300, 'g', 'grain']],
    steps: ['배추·깻잎·고기를 겹겹이 쌓아 냄비에 둘러 담는다.', '동봉된 육수를 붓고 끓인다.', '건더기를 먹은 뒤 칼국수면을 넣어 마무리한다.'],
    kidTip: '맵지 않고 고기·채소가 부드러워서 아이들과 먹기 좋은 1순위 밀키트예요.',
  },
  {
    id: 'kit-bulgogi-jeongol', type: 'main', cuisine: 'korean', kit: true, name: '서울식 불고기전골 밀키트', emoji: '🥘', time: 15, protein: 'beef', oneDish: true,
    ingredients: [['불고기전골 밀키트', 1, '팩', 'kit'], ['밥', 4, '공기', 'pantry']],
    steps: ['양념된 고기와 채소, 당면을 냄비에 담는다.', '동봉 육수를 붓고 고기가 익을 때까지 끓인다.'],
    kidTip: '달짝지근한 국물이라 밥 말아주면 아이들이 잘 먹어요. 당면은 가위로 짧게.',
  },
  {
    id: 'kit-shabu', type: 'main', cuisine: 'korean', kit: true, name: '소고기 샤브샤브 밀키트', emoji: '🍲', time: 20, protein: 'beef', oneDish: true,
    ingredients: [['샤브샤브 밀키트', 1, '팩', 'kit'], ['계란', 2, '개', 'egg'], ['밥', 2, '공기', 'pantry']],
    steps: ['육수를 끓이고 채소를 먼저 넣는다.', '고기를 살짝 데쳐 소스에 찍어 먹는다.', '남은 육수에 밥과 계란을 넣어 죽으로 마무리.'],
    kidTip: '마지막 계란죽은 아이들이 특히 좋아해요.',
  },
  {
    id: 'kit-jjimdak', type: 'main', cuisine: 'korean', kit: true, name: '안동찜닭 밀키트', emoji: '🍗', time: 25, protein: 'chicken', oneDish: true,
    ingredients: [['안동찜닭 밀키트', 1, '팩', 'kit'], ['밥', 4, '공기', 'pantry']],
    steps: ['닭과 양념, 채소를 냄비에 넣고 물을 붓는다.', '뚜껑 덮고 중불에서 닭이 익을 때까지 졸인다.', '당면을 넣고 3분 더 졸인다.'],
    kidTip: '건고추가 들어 있으면 빼고 조리하면 아이들도 먹을 수 있어요.',
  },
  {
    id: 'kit-la-galbi', type: 'main', cuisine: 'korean', kit: true, name: '양념 LA갈비 밀키트', emoji: '🍖', time: 20, protein: 'beef',
    ingredients: [['양념 LA갈비', 1, '팩', 'kit'], ['쌈채소', 1, '봉', 'veg']],
    steps: ['팬을 달군 뒤 갈비를 올려 중불에서 앞뒤로 굽는다.', '양념이 타지 않게 뒤집어가며 익힌다.', '가위로 뼈를 따라 잘라 낸다.'],
    kidTip: '뼈를 발라 한입 크기로 잘라주세요.',
  },
  {
    id: 'kit-pork-galbi', type: 'main', cuisine: 'korean', kit: true, name: '양념 돼지갈비 밀키트', emoji: '🍖', time: 20, protein: 'pork',
    ingredients: [['양념 돼지갈비', 1, '팩', 'kit'], ['쌈채소', 1, '봉', 'veg']],
    steps: ['팬이나 에어프라이어(180℃ 15분)에 굽는다.', '중간에 한 번 뒤집어 양념이 타지 않게 한다.'],
  },
  {
    id: 'kit-jjajang', type: 'main', cuisine: 'korean', kit: true, name: '짜장면 밀키트', emoji: '🍜', time: 15, protein: 'pork', oneDish: true,
    ingredients: [['짜장면 밀키트', 1, '팩', 'kit'], ['오이', 1, '개', 'veg']],
    steps: ['동봉된 짜장소스를 데운다.', '면을 삶아 찬물에 헹군 뒤 다시 따뜻한 물에 데친다.', '면에 소스를 붓고 채 썬 오이를 올린다.'],
    kidTip: '아이들 최애 메뉴! 면을 가위로 잘라주고 턱받이 필수예요.',
  },
  {
    id: 'kit-gungjung-tteok', type: 'main', cuisine: 'korean', kit: true, name: '궁중떡볶이 밀키트', emoji: '🍢', time: 15, protein: 'beef', oneDish: true,
    ingredients: [['궁중떡볶이 밀키트', 1, '팩', 'kit'], ['계란', 4, '개', 'egg']],
    steps: ['떡은 물에 살짝 헹군다.', '고기와 채소를 볶다가 떡과 간장 양념을 넣고 졸인다.', '삶은 계란을 곁들인다.'],
    kidTip: '간장 떡볶이라 맵지 않아요. 4세는 떡을 잘게 잘라주세요.',
  },
  {
    id: 'kit-chadol-doenjang', type: 'main', cuisine: 'korean', kit: true, name: '차돌 된장찌개 밀키트', emoji: '🍲', time: 15, protein: 'beef', oneDish: true,
    ingredients: [['차돌 된장찌개 밀키트', 1, '팩', 'kit'], ['두부', 0.5, '모', 'proc'], ['밥', 4, '공기', 'pantry']],
    steps: ['차돌박이를 먼저 볶아 기름을 낸다.', '물과 된장 소스, 채소를 넣고 끓인다.', '두부를 더 넣으면 양이 넉넉해져요.'],
    kidTip: '청양고추는 어른 그릇에만 넣으세요.',
  },
  {
    id: 'kit-budae', type: 'main', cuisine: 'korean', kit: true, name: '부대찌개 밀키트', emoji: '🍲', time: 20, protein: 'pork', oneDish: true, spicy: true,
    ingredients: [['부대찌개 밀키트', 1, '팩', 'kit'], ['라면사리', 1, '개', 'grain'], ['밥', 4, '공기', 'pantry']],
    steps: ['재료를 냄비에 둘러 담고 양념장을 올린다.', '육수를 붓고 끓인다.', '라면사리를 넣어 마무리.'],
    kidTip: '양념장 풀기 전에 소시지·햄·떡을 아이 몫으로 먼저 건져 맑은 육수에 따로 끓여주세요.',
  },
  {
    id: 'kit-dakgalbi', type: 'main', cuisine: 'korean', kit: true, name: '춘천 닭갈비 밀키트', emoji: '🔥', time: 20, protein: 'chicken', oneDish: true, spicy: true,
    ingredients: [['춘천 닭갈비 밀키트', 1, '팩', 'kit'], ['밥', 2, '공기', 'pantry'], ['김가루', 1, '봉', 'sea']],
    steps: ['팬에 양배추·고구마를 깔고 양념 닭을 올려 볶는다.', '닭이 익으면 떡을 넣고 볶는다.', '남은 양념에 밥과 김가루를 넣어 볶음밥으로.'],
    kidTip: '아이 몫은 양념 넣기 전 고구마·양배추·닭을 덜어 간장으로 따로 볶아주세요.',
  },
  {
    id: 'kit-udon', type: 'main', cuisine: 'korean', kit: true, name: '가쓰오 우동 밀키트', emoji: '🍜', time: 10, protein: 'fish', oneDish: true,
    ingredients: [['우동 밀키트', 1, '팩', 'kit'], ['유부초밥 키트', 1, '봉', 'proc'], ['밥', 2, '공기', 'pantry']],
    steps: ['육수를 끓이고 우동면을 넣어 2분 끓인다.', '어묵·유부 등 고명을 올린다.', '유부초밥을 곁들이면 든든해요.'],
    kidTip: '면을 짧게 잘라 한 김 식혀주세요.',
  },
  {
    id: 'kit-gambas', type: 'western', cuisine: 'western', kit: true, name: '감바스 알 아히요 밀키트', emoji: '🦐', time: 15, protein: 'seafood',
    ingredients: [['감바스 밀키트', 1, '팩', 'kit'], ['바게트', 1, '개', 'grain'], ['스파게티면', 200, 'g', 'grain']],
    steps: ['올리브유에 마늘을 약불로 볶아 향을 낸다.', '새우와 동봉 재료를 넣어 익힌다.', '바게트를 찍어 먹고, 남은 오일에 면을 볶아 파스타로.'],
    kidTip: '페페론치노는 빼고 조리하면 아이들도 잘 먹어요.',
  },
  {
    id: 'kit-toowoomba', type: 'western', cuisine: 'western', kit: true, name: '투움바 파스타 밀키트', emoji: '🍝', time: 15, protein: 'pork',
    ingredients: [['투움바 파스타 밀키트', 1, '팩', 'kit']],
    steps: ['면을 삶는다.', '베이컨과 채소를 볶다가 크림소스를 넣는다.', '면을 넣고 버무린다.'],
    kidTip: '살짝 매콤할 수 있어요. 아이 몫은 소스에 우유를 조금 더 넣어 순하게.',
  },
  {
    id: 'kit-hamburg', type: 'western', cuisine: 'western', kit: true, name: '함박스테이크 밀키트', emoji: '🍔', time: 15, protein: 'beef',
    ingredients: [['함박스테이크 밀키트', 1, '팩', 'kit'], ['계란', 4, '개', 'egg'], ['브로콜리', 1, '송이', 'veg'], ['밥', 4, '공기', 'pantry']],
    steps: ['패티를 약불에 뚜껑 덮어 앞뒤로 굽는다.', '동봉 소스를 데워 붓는다.', '계란 프라이와 데친 브로콜리를 곁들인다.'],
    kidTip: '계란 프라이를 올려 함박 정식처럼 주면 아이들이 좋아해요.',
  },
  {
    id: 'kit-chop-steak', type: 'western', cuisine: 'western', kit: true, name: '찹스테이크 밀키트', emoji: '🥩', time: 15, protein: 'beef',
    ingredients: [['찹스테이크 밀키트', 1, '팩', 'kit'], ['밥', 4, '공기', 'pantry']],
    steps: ['센불에 소고기를 먼저 굽는다.', '채소를 넣고 볶다가 동봉 소스로 버무린다.'],
    kidTip: '고기를 작게 잘라 밥 위에 덮밥처럼 올려주세요.',
  },
];
