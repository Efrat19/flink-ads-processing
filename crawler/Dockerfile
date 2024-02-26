FROM amitgupta4in/scrapy

COPY requirements.txt requirements.txt

RUN pip3 install -r requirements.txt

COPY . .

WORKDIR crawler

ENV SCRAPY_KAFKA_HOSTS ''
ENV SCRAPY_KAFKA_ITEM_PIPELINE_TOPIC ''

ENV SCRAPY_JSON_OUTPUT_PATH ''

CMD ["scrapy","crawl","adcoil"]