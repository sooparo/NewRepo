// 제철 과일 (한국 기준)
// perDay: 4인 가족 하루 아침에 먹을 양 (장보기 합산용)
// tip: 6세·4세 아이 먹일 때 주의할 점

export const FRUITS = [
  { id: 'strawberry', name: '딸기', emoji: '🍓', months: [12, 1, 2, 3, 4, 5], perDay: [0.5, '팩'], tip: '4세는 반으로 잘라주세요.' },
  { id: 'tangerine', name: '귤', emoji: '🍊', months: [11, 12, 1, 2], perDay: [6, '개'] },
  { id: 'hallabong', name: '한라봉', emoji: '🍊', months: [1, 2, 3, 4], perDay: [2, '개'] },
  { id: 'cheonhyehyang', name: '천혜향', emoji: '🍊', months: [2, 3, 4], perDay: [2, '개'] },
  { id: 'kiwi', name: '키위', emoji: '🥝', months: [11, 12, 1, 2, 3], perDay: [3, '개'] },
  { id: 'cherrytomato', name: '방울토마토', emoji: '🍅', months: [4, 5, 6], perDay: [0.3, '팩'], tip: '4세는 4등분해서 주세요(목 걸림 주의).' },
  { id: 'chamoe', name: '참외', emoji: '🍈', months: [5, 6, 7], perDay: [1, '개'] },
  { id: 'watermelon', name: '수박', emoji: '🍉', months: [6, 7, 8], perDay: [0.15, '통'], tip: '씨를 빼고 깍둑썰기 해주세요.' },
  { id: 'blueberry', name: '블루베리', emoji: '🫐', months: [6, 7], perDay: [100, 'g'], tip: '4세는 반으로 눌러 주거나 잘라주세요.' },
  { id: 'melon', name: '멜론', emoji: '🍈', months: [6, 7, 8], perDay: [0.3, '통'] },
  { id: 'peach', name: '복숭아', emoji: '🍑', months: [7, 8], perDay: [2, '개'] },
  { id: 'plum', name: '자두', emoji: '🍑', months: [7], perDay: [4, '개'], tip: '씨를 빼고 잘라주세요.' },
  { id: 'grape', name: '포도', emoji: '🍇', months: [8, 9, 10], perDay: [0.5, '송이'], tip: '4세는 알을 반이나 4등분해서 주세요(목 걸림 주의).' },
  { id: 'fig', name: '무화과', emoji: '🟣', months: [8, 9, 10], perDay: [3, '개'] },
  { id: 'apple', name: '사과', emoji: '🍎', months: [9, 10, 11, 12, 1, 2], perDay: [1, '개'], tip: '아이들은 얇게 썰어 주세요.' },
  { id: 'pear', name: '배', emoji: '🍐', months: [9, 10, 11], perDay: [0.5, '개'] },
  { id: 'persimmon', name: '단감', emoji: '🟠', months: [10, 11], perDay: [2, '개'] },
  { id: 'banana', name: '바나나', emoji: '🍌', months: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12], perDay: [2, '개'], always: true },
];

export const fruitById = new Map(FRUITS.map((f) => [f.id, f]));

/** 해당 월의 제철 과일 (연중 과일인 바나나 제외) */
export function seasonalFruits(month) {
  return FRUITS.filter((f) => !f.always && f.months.includes(month));
}
